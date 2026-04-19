package com.ongo.domain.contentsource.exception

sealed class ContentSourceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ContentSourceNotConnectedException : ContentSourceException("구글 드라이브가 연결되지 않았습니다")

class ContentSourceExpiredException(reason: String) : ContentSourceException("인증이 만료되었습니다: $reason")

class ContentSourceRevokedException(reason: String) : ContentSourceException("권한이 회수되었습니다: $reason")

class DriveFileNotFoundException(fileId: String) : ContentSourceException("드라이브 파일을 찾을 수 없습니다: $fileId")

class DriveFilePermissionDeniedException(fileId: String) : ContentSourceException("파일 접근 권한이 없습니다: $fileId")

class DuplicateDriveImportException(fileId: String) : ContentSourceException("이미 가져온 적이 있는 파일입니다: $fileId")

class ConcurrentImportLimitException(limit: Int) : ContentSourceException("동시에 가져올 수 있는 파일은 최대 ${limit}개입니다")

class OAuthStateMismatchException : ContentSourceException("OAuth state 검증 실패")

class DriveDownloadFailedException(cause: Throwable) : ContentSourceException("드라이브 다운로드 실패: ${cause.message}", cause)
