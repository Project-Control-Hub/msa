package com.pch.file.exception;

import com.pch.common.web.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 서비스 전역 예외 핸들러. pch-common 의 GlobalExceptionHandler 를 상속.
 */
@RestControllerAdvice
public class ServiceExceptionHandler extends GlobalExceptionHandler {
}
