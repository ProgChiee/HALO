package com.ptc.halo.service;
public class AccountStateException extends RuntimeException {
    public final String code;
    public AccountStateException(boolean archived) {
        super(archived ? "This account is archived. Please contact an administrator if you believe this is a mistake." : "This account is currently inactive. Please contact an administrator.");
        code = archived ? "ACCOUNT_ARCHIVED" : "ACCOUNT_INACTIVE";
    }
}
