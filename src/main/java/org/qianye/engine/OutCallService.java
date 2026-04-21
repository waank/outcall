package org.qianye.engine;

import org.qianye.entity.OutboundCallTaskDO;

public interface OutCallService {
    void outCall();

    void executeGroupOutCall(OutboundCallTaskDO task);
}
