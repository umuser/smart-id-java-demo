package ee.sk.siddemo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.v3.SmartIdClient;
import ee.sk.smartid.v3.rest.dao.SessionStatus;

@Service
public class SmartIdV3SessionsStatusService {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdV3SessionsStatusService.class);

    private final SmartIdClient smartIdClientV3;

    public SmartIdV3SessionsStatusService(SmartIdClient smartIdClientV3) {
        this.smartIdClientV3 = smartIdClientV3;
    }

    @Async
    public void getSessionStatus(String sessionId, AsyncCallback<SessionStatus> callback) {
        logger.debug("Starting to poll session status for session {}", sessionId);
        try {
            SessionStatus sessionStatus = smartIdClientV3.createSessionStatusPoller().fetchFinalSessionStatus(sessionId);
            if (sessionStatus.getState().equals("COMPLETE")) {
                logger.debug("Session state: {}", sessionStatus.getState());
                callback.onComplete(sessionStatus, null);
            }
        } catch (SmartIdClientException ex) {
            logger.error("Error occurred while fetching session status", ex);
            callback.onComplete(null, ex);
        }
    }
}
