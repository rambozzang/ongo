package com.ongo.common.annotation

import com.ongo.common.enums.Permission

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class RequiresPermission(val value: Permission)
