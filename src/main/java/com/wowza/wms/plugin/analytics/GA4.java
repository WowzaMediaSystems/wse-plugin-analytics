package com.wowza.wms.plugin.analytics;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.*;
import com.wowza.wms.plugin.analytics.model.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class GA4
{
    private static final Class CLASS = GA4.class;
    private static final String CLASSNAME = CLASS.getSimpleName();
    private final IApplicationInstance appInstance;
    private final WMSLogger logger;
    private final String gtag;
    private final String endpoint;
    private final boolean debugEnabled;
    private final String tagManagerPreviewString;

    public GA4(IApplicationInstance appInstance, String gtag, String googleUrlDomain, boolean debugEnabled, String tagManagerPreviewString)
    {
        this.appInstance = appInstance;
        this.logger = WMSLoggerFactory.getLoggerObj(CLASS, appInstance);
        this.gtag = gtag;
        this.endpoint = String.format("https://%s/g/collect", googleUrlDomain);
        this.debugEnabled = debugEnabled;
        this.tagManagerPreviewString = tagManagerPreviewString;
    }

    public void sendEvent(SessionInfo sessionInfo, VideoEvent event)
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", sessionInfo.getUserAgent());
        if (tagManagerPreviewString != null)
            headers.put("x-gtm-server-preview", tagManagerPreviewString);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("v", "2");
        paramMap.put("tid", gtag);
        paramMap.put("cid", sessionInfo.getGaClientId());
        paramMap.put("sid", sessionInfo.getSessionId());
        paramMap.put("dl", sessionInfo.getPage());
        if (sessionInfo.getReferer() != null)
            paramMap.put("dr", sessionInfo.getReferer());
        paramMap.put("_uip", sessionInfo.getIpAddress());
        paramMap.put("en", event.getEventType());
        event.getGAEventParams().forEach((k, v) -> paramMap.put("ep." + k, URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8)));
        if (sessionInfo.isFirstVisit())
            paramMap.put("_fv", "1");
        else
            paramMap.put("_et", String.valueOf(sessionInfo.getEventElapsedTime()));
        if (debugEnabled)
            paramMap.put("_dbg", "1");

        if (debugEnabled)
            logger.info(CLASSNAME + ".sendEvent [" + appInstance.getContextStr() + "] params: " + paramMap);
        String params = paramMap.entrySet().stream()
                .map(param -> param.getKey() + "=" + param.getValue())
                .collect(Collectors.joining("&"));
        URI uri = URI.create(endpoint + "?" + params);

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .POST(bodyPublisher);
        headers.forEach(builder::header);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                .whenCompleteAsync((response, exception) -> {
                    if (exception != null)
                        logger.error(CLASSNAME + ".sendEvent [" + appInstance.getContextStr() + "] request: " + uri, exception);
                    else if (debugEnabled)
                        logger.info(CLASSNAME + ".sendEvent [" + appInstance.getContextStr() + "] request: " + uri + ", response: " + response.statusCode());
                 });
    }
}
