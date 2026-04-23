package org.qianye.common;

import lombok.Data;

@Data
public class OutCallResult {
    public static final String RETRY_REASON = "retryReason";
    public static final String STATUS_INVALID = "statusInvalid";
    public static final String STOP_REASON = "stopReason";
    public static final String FAIL_REASON = "failReason";
    public static final String NOT_MATCH_TIME = "notMatchTime";
    public static final String SLOT_LIMIT = "slotLimit";
    public static final String EXECUTE_FINISH = "executeFinish";
    public static final String MAX_RETRIES = "maxRetries";
    public static final String QUEUE_LIMIT = "queueLimit";
    public static final String POOL_FULL = "poolFull";
    public static final String UNKNOWN_ERROR = "unknownError";
    public static final String CALLER_NOT_EXIST = "callerNotExist";
    public static final String RECALL_REASON = "recallReason";
    public static final String MACHINE_DOWN = "machineDown";
    public static final String OUT_FIXED_TIME = "outFixedTime";
    public static final String CALL_NOT_FOUND = "callNotFound";
    public static final String CALL_FAIL = "callFail";
    private String acid;
    private boolean success;
    private String errorCode;
    private String errorMsg;

    public static OutCallResult success(String acid) {
        OutCallResult r = new OutCallResult();
        r.setSuccess(true);
        r.setAcid(acid);
        return r;
    }

    public static OutCallResult fail(String errorCode, String errorMsg) {
        OutCallResult r = new OutCallResult();
        r.setSuccess(false);
        r.setErrorCode(errorCode);
        r.setErrorMsg(errorMsg);
        return r;
    }

    public static OutCallResult failForSlotLimit() {
        return fail(FAIL_REASON, SLOT_LIMIT);
    }
}
