package org.meldtech.platform.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.meldtech.platform.model.service.AppClientConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Objects;

@Slf4j
@Controller
public class ErrorMapping implements ErrorController {

    private final AppClientConfigService appConfigService;

    public ErrorMapping(AppClientConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        String appId = request.getParameter("appId");
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object requestPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exceptionType = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);

        if (Objects.nonNull(status)) {
            int statusCode = Integer.parseInt(status.toString());
            log.error("Status code: {}", statusCode);
            log.error("Request path: {}", requestPath);
            log.error("Message: {}", message);
            log.error("Exception type: {}", exceptionType);

            if(statusCode == HttpStatus.NOT_FOUND.value()) {
                return "redirect:"+appConfigService.getResolvedUrl(appId);
//                return "redirect:"+notFoundPath;
            }
        }
        return "error";
    }
}
