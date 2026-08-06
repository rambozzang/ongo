-- approvals.user_id 의 ON DELETE CASCADE 를 NO ACTION 으로 바꾼다.
--
-- approvals 는 users 를 세 번 참조한다.
--   user_id      소유자   ON DELETE CASCADE   <- 이것만 CASCADE 였다
--   requester_id 요청자   NO ACTION
--   reviewer_id  검토자   NO ACTION
--
-- 한 행이 서로 다른 세 사용자를 가리킬 수 있다. 그래서 소유자를 지우면 DB 가 행을
-- 통째로 지우고 **요청자와 검토자의 승인 데이터가 함께 사라진다.** 실제 PostgreSQL 로
-- 재현했다 — 혼합 행 1건을 만들고 소유자를 삭제하니 행이 사라졌다.
--
-- 애플리케이션 층에서는 못 막는다. DB 가 하는 일이다. 계정 삭제 정책 레지스트리에서
-- approvals 는 ROW_BLOCK 이라 삭제 엔진은 애초에 진행하지 않지만, 엔진을 우회한
-- 삭제나 정책 회귀에서도 조용한 유실이 없어야 한다.
--
-- 지금 드러나지 않은 이유는 탈퇴가 다른 외래키에 막혀 실행되지 않기 때문이다.
-- self-service 탈퇴를 여는 순간 표면화되므로 그 전에 닫는다.
--
-- 가장 보수적인 상태를 택한다. NOT NULL 을 유지한 채 NO ACTION 으로만 바꾼다.
-- 즉 이 행이 있으면 소유자 삭제가 "막힌다". 조용히 지워지는 것보다 막히는 게 낫다.
-- ROW_DETACH(nullable + SET NULL)는 그 정책을 채택하기로 결정한 뒤 별도로 한다.
--
-- 근거: docs/plans/account-deletion-policy-table.md §1.2, §9.1
--
-- 제약 이름은 그대로 유지한다. 정책 레지스트리(UserFkPolicyRegistry)가 제약 이름을
-- 키로 쓰므로 이름이 바뀌면 미분류로 잡혀 계정 삭제가 전역 차단된다.

DO $$
DECLARE
    v_deltype "char";
    v_columns text;
BEGIN
    -- 현재 상태를 먼저 읽는다. 스키마를 public 으로 명시하고 컬럼까지 확인한다.
    SELECT c.confdeltype,
           (SELECT string_agg(a.attname, ',' ORDER BY k.ord)
              FROM unnest(c.conkey) WITH ORDINALITY k(attnum, ord)
              JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
      INTO v_deltype, v_columns
      FROM pg_constraint c
      JOIN pg_class t      ON t.oid = c.conrelid
      JOIN pg_namespace n  ON n.oid = t.relnamespace
     WHERE n.nspname = 'public'
       AND t.relname = 'approvals'
       AND c.conname = 'approvals_user_id_fkey'
       AND c.contype  = 'f';

    -- 제약이 없으면 조용히 넘어가지 않는다. 이름이 바뀌었거나 누가 지운 것이고,
    -- 둘 다 정책 레지스트리가 이 제약을 이름으로 찾지 못하는 상태다.
    IF v_deltype IS NULL THEN
        RAISE EXCEPTION
            'approvals_user_id_fkey 외래키를 public.approvals 에서 찾지 못했다. '
            'UserFkPolicyRegistry 가 제약 이름을 키로 쓰므로 이름이 바뀌면 계정 삭제가 전역 차단된다';
    END IF;

    IF v_columns IS DISTINCT FROM 'user_id' THEN
        RAISE EXCEPTION
            'approvals_user_id_fkey 의 컬럼이 예상과 다르다: %. user_id 단일 컬럼이어야 한다', v_columns;
    END IF;

    IF v_deltype = 'c' THEN
        -- CASCADE -> NO ACTION. 같은 이름으로 다시 만든다.
        ALTER TABLE public.approvals DROP CONSTRAINT approvals_user_id_fkey;

        -- 삭제 규칙을 명시하지 않으면 NO ACTION 이다.
        -- 기존 행의 참조 무결성은 이전 제약이 보장해 왔으므로 재생성 검증에 걸리지 않는다.
        ALTER TABLE public.approvals
            ADD CONSTRAINT approvals_user_id_fkey
            FOREIGN KEY (user_id) REFERENCES public.users(id);

    ELSIF v_deltype = 'a' THEN
        -- 이미 NO ACTION. 재적용이라 아무것도 하지 않는다.
        NULL;

    ELSE
        -- SET NULL('n'), RESTRICT('r'), SET DEFAULT('d') 등 예상 밖 상태.
        -- 이 마이그레이션이 전제한 것과 다르므로 조용히 통과시키지 않는다.
        RAISE EXCEPTION
            'approvals_user_id_fkey 의 삭제 규칙이 예상 밖이다: %. '
            'CASCADE(c) 또는 NO ACTION(a) 만 처리한다. ROW_DETACH 정책을 채택했다면 '
            '이 마이그레이션 대신 그 결정에 맞는 마이그레이션이 필요하다', v_deltype;
    END IF;

    -- 최종 확인. 위 분기를 어떻게 지나왔든 결과는 NO ACTION 이어야 한다.
    SELECT c.confdeltype INTO v_deltype
      FROM pg_constraint c
      JOIN pg_class t      ON t.oid = c.conrelid
      JOIN pg_namespace n  ON n.oid = t.relnamespace
     WHERE n.nspname = 'public'
       AND t.relname = 'approvals'
       AND c.conname = 'approvals_user_id_fkey'
       AND c.contype  = 'f';

    IF v_deltype IS DISTINCT FROM 'a' THEN
        RAISE EXCEPTION
            '마이그레이션 후에도 approvals_user_id_fkey 가 NO ACTION 이 아니다: %', v_deltype;
    END IF;
END $$;

COMMENT ON CONSTRAINT approvals_user_id_fkey ON approvals IS
    '소유자 삭제 시 행을 지우지 않는다. 같은 행의 requester_id/reviewer_id 가 다른 사용자를 가리킬 수 있어 CASCADE 면 남의 데이터가 함께 사라진다';
