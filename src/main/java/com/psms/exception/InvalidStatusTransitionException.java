package com.psms.exception;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String from, String to) {
        super("Không thể chuyển trạng thái từ " + from + " sang " + to);
    }
}
