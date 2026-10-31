package io.takamiya.mio.dsl;

import cn.fsy.plugin.sdl.library.SdlException;
import cn.fsy.plugin.sdl.library.manage.install.InstallException;

public final class InstallErrors {

    private InstallErrors() {
    }

    public static String describe(SdlException e) {
        if (e == null) {
            return "未知错误";
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (e.getCause() instanceof InstallException) {
            InstallException.ErrorCode code =
                    ((InstallException) e.getCause()).getErrorCode();
            if (code == InstallException.ErrorCode.VERSION_DOWNGRADE) {
                return "版本过低（不允许降级）：" + msg;
            }
            if (code == InstallException.ErrorCode.SIGNATURE_MISMATCH) {
                return "签名不符：" + msg;
            }
        }
        if (msg.isEmpty()) {
            return e.getErrorCode().getDescription();
        }
        return msg;
    }
}
