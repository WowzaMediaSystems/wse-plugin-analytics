package com.wowza.wms.plugin.analytics;

import com.wowza.util.*;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.client.IClient;
import com.wowza.wms.httpstreamer.model.*;
import com.wowza.wms.mediacaster.*;
import com.wowza.wms.medialist.*;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.plugin.analytics.model.*;
import com.wowza.wms.rtp.model.*;
import com.wowza.wms.stream.*;
import com.wowza.wms.util.*;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.webrtc.model.WebRTCCommandRequest;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.*;

public class ModuleGA4 extends ModuleBase
{
    private static final Class<ModuleGA4> CLASS = ModuleGA4.class;
    private static final String CLASSNAME = CLASS.getSimpleName();
    private static final String DEFAULT_VOD_PERCENTAGES = "10, 25, 50, 75";
    private IApplicationInstance appInstance;
    private String gtag;
    private boolean googleDebugEnabled = false;
    private String googleUrlDomain = "www.google-analytics.com";
    private String tagManagerPreviewString;
    private boolean sendPublishEvents = false;
    private final Map<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    private GA4 ga4;
    private String provider = "Wowza Streaming Engine";
    private int[] vodPercentages = {10, 25, 50, 75};
    private int liveUpdateFrequency = 300;
    private String ipOverride;

    private Timer sessionUpdateTimer;

    public void onAppCreate(IApplicationInstance appInstance)
    {
        try
        {
            this.appInstance = appInstance;
            this.gtag = Objects.requireNonNull(appInstance.getProperties().getPropertyStr("ga4MeasurementId"), "ga4MeasurementId not set");
            this.googleUrlDomain = appInstance.getProperties().getPropertyStr("ga4GoogleUrlDomain", googleUrlDomain);
            googleDebugEnabled = appInstance.getProperties().getPropertyBoolean("ga4DebugEnabled", googleDebugEnabled);
            tagManagerPreviewString = appInstance.getProperties().getPropertyStr("ga4TagMangerPreviewString");
            ipOverride = appInstance.getProperties().getPropertyStr("ga4DebugIpOverride", ipOverride);
            provider = appInstance.getProperties().getPropertyStr("ga4VideoProvider", provider);
            String vodPercentageStr = appInstance.getProperties().getPropertyStr("ga4VodPercentages", DEFAULT_VOD_PERCENTAGES);
            vodPercentages = Arrays.stream(vodPercentageStr.split(","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .sorted()
                    .toArray();
            liveUpdateFrequency = appInstance.getProperties().getPropertyInt("ga4LiveUpdateFrequency", liveUpdateFrequency);
            ga4 = new GA4(appInstance, gtag, googleUrlDomain, googleDebugEnabled, tagManagerPreviewString);

            sendPublishEvents = appInstance.getProperties().getPropertyBoolean("ga4SendPublishEvents", sendPublishEvents);
            if (sendPublishEvents)
                appInstance.addMediaCasterListener(new MediaCasterListener());
            sessionUpdateTimer = new Timer(CLASSNAME + "[" + appInstance.getContextStr() + "-session-update-timer]", true);
            sessionUpdateTimer.scheduleAtFixedRate(new ProgressUpdateTask(), 10000, 10000);
            getLogger(CLASS, appInstance).info(String.format("%s.onAppCreate [%s] version: %s, gtag: %s, google domain: %s, " +
                            "debugEnabled: %b, tagManagerPreviewString: %s, ipOverride: %s, provider: %s, vodPercentages: %s, " +
                            "liveUpdateFrequency: %d, sendPublishEvents: %b",
                    CLASSNAME, appInstance.getContextStr(), BuildProperties.getVersion(), gtag, googleUrlDomain,
                    googleDebugEnabled, tagManagerPreviewString, ipOverride, provider, Arrays.toString(vodPercentages),
                    liveUpdateFrequency, sendPublishEvents));
        }
        catch (Exception e)
        {
            getLogger(CLASS, appInstance).error(CLASSNAME + ".onAppStart [" + appInstance.getContextStr() + "] exception: " + e.getMessage(), e);
        }
    }

    public void onAppStop(IApplicationInstance appInstance)
    {
        sessionUpdateTimer.cancel();
        sessionUpdateTimer = null;
    }

    public void onStreamCreate(IMediaStream stream)
    {
        stream.addClientListener(new StreamListener());
    }

    public void onHTTPStreamerRequest(IHTTPStreamerSession session, IHTTPStreamerRequestContext context)
    {
        try
        {
            if (!isTrigger(context))
                return;
            String sessionId = session.getSessionId();
            SessionInfo sessionInfo = Objects.requireNonNull(sessionInfoMap.computeIfAbsent(sessionId, info -> getHttpSessionInfo(session)));
            String eventType = sessionInfo.isNewSession() ? "video_start" : "video_progress";
            sendVideoEvent(sessionInfo, eventType, context);
        }
        catch (Exception e)
        {
            getLogger(CLASS, appInstance).error(CLASSNAME + ".onHttpSessionCreate [" + appInstance.getContextStr() + "] error sending stats to GA4", e);
        }
    }

    private boolean isTrigger(IHTTPStreamerRequestContext context)
    {
        List<Integer> triggers = List.of(
                IHTTPStreamerRequestContext.CUPERTINO_MEDIA,
                IHTTPStreamerRequestContext.MPEGDASH_MEDIA
        );
        return triggers.contains(context.getRequestType());
    }

    public void onHTTPSessionDestroy(final IHTTPStreamerSession session)
    {
        String sessionId = session.getSessionId();
        SessionInfo sessionInfo = sessionInfoMap.remove(sessionId);
        sendVideoStopEvent(sessionInfo, "video_complete");
    }

    private void sendVideoStopEvent(SessionInfo sessionInfo, String eventType)
    {
        try
        {
            if (sessionInfo != null && !sessionInfo.isNewSession())
            {
                VideoEvent event = new VideoEvent(eventType, sessionInfo.getStreamName(), sessionInfo.getUri(), sessionInfo.getProtocol(), sessionInfo.getType());
                event.setProvider(provider);
                event.setCurrentTime(sessionInfo.getVideoCurrentTime());
                if (sessionInfo.getType().equals("vod"))
                {
                    event.setVodPercent(sessionInfo.getVodPercent());
                    event.setDuration(sessionInfo.getVodDuration());
                }
                else
                    event.setLiveProgress((int) sessionInfo.getVideoCurrentTime());
                if (ga4 != null)
                    ga4.sendEvent(sessionInfo, event);
            }
        }
        catch (Exception e)
        {
            getLogger(CLASS, appInstance).error(CLASSNAME + ".sendVideoCompleteEvent [" + appInstance.getContextStr() + "] error sending stats to GA4", e);
        }
    }

    private void sendVideoEvent(SessionInfo sessionInfo, String eventType, IHTTPStreamerRequestContext context)
    {
        VideoEvent event = new VideoEvent(eventType, sessionInfo.getStreamName(), sessionInfo.getUri(), sessionInfo.getProtocol(), sessionInfo.getType());
        event.setProvider(provider);
        event.setRendition(getRenditionFromRequest(context));
        event.setCurrentTime(sessionInfo.getVideoCurrentTime());
        boolean sendEvent = false;
        if (eventType.equals("video_progress") && sessionInfo.sendProgressUpdate())
        {
            if (sessionInfo.getType().equals("vod"))
            {
                event.setVodPercent(sessionInfo.getLastProgress());
                event.setDuration(sessionInfo.getVodDuration());
            }
            else
                event.setLiveProgress(sessionInfo.getLastProgress());
            sendEvent = true;
        }
        else if (!eventType.equals("video_progress"))
            sendEvent = true;
        if (googleDebugEnabled)
            getLogger(CLASS, appInstance).info(CLASSNAME + ".sendVideoEvent [" + appInstance.getContextStr() + "/" + sessionInfo.getStreamName() + "] sendEvent: " + sendEvent + ", event: " + event);
        if (ga4 != null && sendEvent)
            ga4.sendEvent(sessionInfo, event);
    }

    private String getRenditionFromRequest(IHTTPStreamerRequestContext context)
    {
        String rendition = null;
        if (context != null)
        {
            String path = context.getRequest().getPath();

            String mediaPart = path.substring(path.lastIndexOf("/") + 1);
            if (isVideo(mediaPart))
            {
                rendition = getRendition(mediaPart);
                if (rendition != null)
                    return rendition;
            }
            else
                rendition = getRendition(mediaPart);
        }
        return rendition;
    }

    private String getRendition(String mediaPart)
    {
        Pattern pattern = Pattern.compile("(?<=_t64)(?:[A-Za-z\\d+/]{4})*(?:[A-Za-z\\d+/]{2}==|[A-Za-z\\d+/]{3}=)?");
        Matcher matcher = pattern.matcher(mediaPart);
        if (matcher.find())
        {
            String base64 = matcher.group();
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        }
        pattern = Pattern.compile("(?<=_b)\\d+");
        matcher = pattern.matcher(mediaPart);
        if (matcher.find())
            return matcher.group();
        pattern = Pattern.compile("(?<=_ridp\\d{1,2}[av]a\\d{1,2}br)\\d+");
        matcher = pattern.matcher(mediaPart);
        if (matcher.find())
            return matcher.group();
        return null;
    }

    private boolean isVideo(String mediaPart)
    {
        return !mediaPart.contains("_ao") && !mediaPart.contains("_ctaudio");
    }

    private String getStreamUrl(IHTTPStreamerSession session)
    {
        String scheme = session.isSecure() ? "https://" : "http://";
        String server = session.getServerIp();
        String portStr = isHttpPort(session.getServerPort()) ? "" : ":" + session.getServerPort();
        String uri = session.getUri();
        return scheme + server + portStr + "/" + uri;
    }

    private boolean isHttpPort(int serverPort)
    {
        return serverPort == 80 || serverPort == 443;
    }

    private SessionInfo getHttpSessionInfo(IHTTPStreamerSession session)
    {
        try
        {
            String sessionId = session.getSessionId();
            MessageDigest md = MessageDigest.getInstance("MD5");
            String ipAddress = session.getIpAddress();
            if (ipOverride != null)
                ipAddress = ipOverride;
            String userAgent = session.getUserAgent();
            String input = ipAddress + "::" + userAgent;
            String gaClientId = BufferUtils.encodeHexString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
            String streamName = session.getStreamName();
            String uri = getStreamUrl(session);
            String page = uri.substring(0, uri.lastIndexOf("/"));
            String referer = session.getReferrer();
            String type = getAppType();
            String protocol = getProtocol(session);
            ElapsedTimer timer = session.getElapsedTime();
            SessionInfo sessionInfo = new SessionInfo(sessionId, ipAddress, userAgent, gaClientId, streamName, uri, page, referer, type, protocol, timer);
            if (type.equals("vod"))
            {
                sessionInfo.setVodDuration(getVodDuration(streamName, session.getStreamExt()));
                sessionInfo.setVodUpdatePercentages(vodPercentages);
            }
            else
                sessionInfo.setProgressUpdateFrequency(liveUpdateFrequency);
            return sessionInfo;
        }
        catch (NoSuchAlgorithmException e)
        {
            getLogger(CLASS, appInstance).error(CLASSNAME + ".getHTTPSessionInfo [" + appInstance.getContextStr() + "] error sending stats to GA4", e);
        }
        return null;
    }

    private String getAppType()
    {
        String streamType = appInstance.getStreamType().toLowerCase();
        return streamType.matches("default|record|file") ? "vod" : "live";
    }

    private double getVodDuration(String streamName, String ext)
    {
        String[] decoded = ModuleUtils.decodeStreamExtension(streamName, ext);
        streamName = decoded[0];
        ext = decoded[1];
        if (appInstance.getMediaReaderContentType(ext) == IMediaReader.CONTENTTYPE_MEDIALIST)
            streamName = getStreamNameFromMediaList(streamName, ext);
        return StreamUtils.getStreamLength(appInstance, streamName);
    }

    private String getStreamNameFromMediaList(String mediaListName, String ext)
    {
        String streamName = mediaListName;
        try
        {
            MediaList mediaList = Objects.requireNonNull(MediaListUtils.parseMediaList(appInstance, mediaListName, ext, null), "MediaList is null");
            MediaListSegment segment = Objects.requireNonNull(mediaList.getFirstSegment(), "MediaList is empty");
            for (MediaListRendition rendition : segment.getRenditions())
            {
                int type = rendition.getType();
                if (type == IVHost.CONTENTTYPE_VIDEO || type == IVHost.CONTENTTYPE_AUDIO)
                {
                    streamName = rendition.getName();
                    break;
                }
            }
        }
        catch (Exception e)
        {
            getLogger(CLASS, appInstance).warn(CLASSNAME + ".getStreamNameFromMediaList [" + appInstance.getContextStr() + "/" + mediaListName + "] error getting media file from mediaList: " + e);
        }

        return streamName;
    }

    private String getProtocol(IHTTPStreamerSession session)
    {
        String connectionProtocol;
        switch (session.getSessionProtocol())
        {
            case IHTTPStreamerSession.SESSIONPROTOCOL_CUPERTINOSTREAMING:
                connectionProtocol = "HLS";
                break;
            case IHTTPStreamerSession.SESSIONPROTOCOL_MPEGDASHSTREAMING:
                connectionProtocol = "Dash";
                break;
            default:
                connectionProtocol = "HTTP";
        }
        return connectionProtocol;
    }

    private class StreamListener extends MediaStreamActionNotifyBase
    {
        @Override
        public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset)
        {
            // not implementing rtmp playback as it's pretty much not supported anywhere now.
            RTPStream rtpStream = stream.getRTPStream();
            if (rtpStream != null)
            {
                RTPSession rtpSession = rtpStream.getSession();
                if (rtpSession != null)
                {
                    SessionInfo sessionInfo = Objects.requireNonNull(sessionInfoMap.computeIfAbsent(rtpSession.getSessionId(), info -> getRTPSessionInfo(rtpSession, streamName, true)));
                    if (sessionInfo.isNewSession())
                        sendVideoEvent(sessionInfo, "video_start", null);
                }
            }
        }

        private SessionInfo getRTPSessionInfo(RTPSession session, String streamName, boolean isPlaySession)
        {
            try
            {
                String sessionId = session.getSessionId();
                String ipAddress = session.getIp();
                if (ipOverride != null)
                    ipAddress = ipOverride;
                String userAgent = session.getUserAgent();
                String input = ipAddress + "::" + userAgent;
                MessageDigest md = MessageDigest.getInstance("MD5");
                String gaClientId = BufferUtils.encodeHexString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
                URI uri;
                if (session.isWebRTC())
                {
                    WebRTCCommandRequest request = session.getWebRTCSession().getCommandRequest();
                    uri = new URI("wss", request.getOriginalRequestDomainName(), "/" + appInstance.getContextStr() + "/" + streamName, null);
                }
                else
                    uri = URI.create(session.getUri());
                String page = uri.getPath();
                String referer = session.getReferrer();
                String type = getAppType();
                String protocol = session.isWebRTC() ? "WebRTC" : session.isDescribe() ? "RTSP" : "RTP";
                ElapsedTimer timer = session.getElapsedTime();
                SessionInfo sessionInfo = new SessionInfo(sessionId, ipAddress, userAgent, gaClientId, streamName, uri.toString(), page, referer, type, protocol, timer);
                if (type.equals("vod"))
                {
                    sessionInfo.setVodDuration(getVodDuration(streamName, MediaStream.MP4_STREAM_EXT));
                    sessionInfo.setVodUpdatePercentages(vodPercentages);
                }
                else
                {
                    sessionInfo.setProgressUpdateFrequency(liveUpdateFrequency);
                    if (!isPlaySession)
                        sessionInfo.setIsPlaySession(false);
                }
                return sessionInfo;
            }
            catch (NoSuchAlgorithmException | URISyntaxException e)
            {
                getLogger(CLASS, appInstance).error(CLASSNAME + ".getRTPSessionInfo [" + appInstance.getContextStr() + "/" + streamName + "] error sending stats to GA4", e);
            }
            return null;
        }

        @Override
        public void onStop(IMediaStream stream)
        {
            // not implementing rtmp playback as it's pretty much not supported anywhere now.
            RTPStream rtpStream = stream.getRTPStream();
            if (rtpStream != null)
            {
                RTPSession session = rtpStream.getSession();
                if (session != null)
                {
                    String sessionId = session.getSessionId();
                    SessionInfo sessionInfo = sessionInfoMap.remove(sessionId);
                    sendVideoStopEvent(sessionInfo, "video_complete");
                }
            }
        }

        @Override
        public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
        {
            try
            {
                if (!sendPublishEvents || appInstance.getMediaCasterStreams().getMediaCaster(streamName) != null)
                    return;
                SessionInfo sessionInfo = null;
                IClient client = stream.getClient();
                if (client != null)
                    sessionInfo = Objects.requireNonNull(sessionInfoMap.computeIfAbsent(String.valueOf(client.getClientId()), info -> getRTMPSessionInfo(client, streamName)));
                RTPStream rtpStream = stream.getRTPStream();
                if (rtpStream != null)
                {
                    RTPSession rtpSession = rtpStream.getSession();
                    if (rtpSession != null)
                        sessionInfo = Objects.requireNonNull(sessionInfoMap.computeIfAbsent(rtpSession.getSessionId(), info -> getRTPSessionInfo(rtpSession, streamName, false)));
                }
                if (sessionInfo == null)
                    return; // not firing events for internal streams

                if (sessionInfo.isNewSession())
                    sendVideoEvent(sessionInfo, "video_publish", null);
            }
            catch (Exception e)
            {
                getLogger(CLASS, appInstance).error(CLASSNAME + ".onPublish [" + stream.getContextStr() + "] exception: " + e, e);
            }
        }

        private SessionInfo getRTMPSessionInfo(IClient client, String streamName)
        {
            try
            {
                String sessionId = String.valueOf(client.getClientId());
                String ipAddress = client.getIp();
                if (ipOverride != null)
                    ipAddress = ipOverride;
                String userAgent = client.getFlashVer();
                String input = ipAddress + "::" + userAgent;
                MessageDigest md = MessageDigest.getInstance("MD5");
                String gaClientId = BufferUtils.encodeHexString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
                URI uri = URI.create(client.getUri() + "/" + streamName);
                String page = uri.getPath();
                String referer = client.getReferrer();
                String type = getAppType();
                String protocol = "RTMP";
                ElapsedTimer timer = client.getElapsedTime();
                SessionInfo sessionInfo = new SessionInfo(sessionId, ipAddress, userAgent, gaClientId, streamName, uri.toString(), page, referer, type, protocol, timer);
                sessionInfo.setIsPlaySession(false);
                return sessionInfo;
            }
            catch (Exception e)
            {
                getLogger(CLASS, appInstance).error(CLASSNAME + ".getRTMPSessionInfo [" + appInstance.getContextStr() + "/" + streamName + "] error sending stats to GA4", e);
            }
            return null;
        }

        @Override
        public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
        {
            if (!sendPublishEvents || appInstance.getMediaCasterStreams().getMediaCaster(streamName) != null)
                return;
            String sessionId = null;
            IClient client = stream.getClient();
            if (client != null)
                sessionId = String.valueOf(client.getClientId());
            else if (stream.getRTPStream() != null)
                sessionId = stream.getRTPStream().getSession().getSessionId();
            if (sessionId != null)
            {
                SessionInfo sessionInfo = sessionInfoMap.remove(sessionId);
                sendVideoStopEvent(sessionInfo, "video_unpublish");
            }
        }
    }

    private class MediaCasterListener extends MediaCasterNotifyBase
    {
        @Override
        public void onStreamStart(IMediaCaster mediaCaster)
        {
            String sessionId = String.valueOf(mediaCaster.getMediaCasterStreamItem().getUniqueId());
            SessionInfo sessionInfo = Objects.requireNonNull(sessionInfoMap.computeIfAbsent(sessionId, info -> getMediaCasterSessionInfo(mediaCaster, sessionId)));
            if (sessionInfo.isNewSession())
                sendVideoEvent(sessionInfo, "video_publish", null);
        }

        private SessionInfo getMediaCasterSessionInfo(IMediaCaster mediaCaster, String sessionId)
        {
            String streamName = mediaCaster.getMediaCasterId();
            try
            {
                String userAgent = provider;
                MessageDigest md = MessageDigest.getInstance("MD5");
                String gaClientId = BufferUtils.encodeHexString(md.digest(userAgent.getBytes(StandardCharsets.UTF_8)));
                String page = appInstance.getContextStr() + "/" + mediaCaster.getMediaCasterId();
                String type = getAppType();
                ElapsedTimer timer = mediaCaster.getStream().getElapsedTime();
                SessionInfo sessionInfo = new SessionInfo(sessionId, null, userAgent, gaClientId, streamName, null, page, null, type, "mediaCaster", timer);
                sessionInfo.setIsPlaySession(false);
                return sessionInfo;
            }
            catch (NoSuchAlgorithmException e)
            {
                getLogger(CLASS, appInstance).error(CLASSNAME + ".getMediaCasterSessionInfo [" + appInstance.getContextStr() + "/" + streamName + "] error sending stats to GA4", e);
            }
            return null;
        }

        @Override
        public void onStreamStop(IMediaCaster mediaCaster)
        {
            String sessionId = String.valueOf(mediaCaster.getMediaCasterStreamItem().getUniqueId());
            SessionInfo sessionInfo = sessionInfoMap.remove(sessionId);
            sendVideoStopEvent(sessionInfo, "video_unpublish");
        }
    }

    private class ProgressUpdateTask extends TimerTask
    {
        @Override
        public void run()
        {
            sendNonHttpProgressEvents();
        }

        private void sendNonHttpProgressEvents()
        {
            sessionInfoMap.values().stream()
                    .filter(sessionInfo -> sessionInfo.getProtocol().matches("WebRTC|RTSP|RTP"))
                    .filter(SessionInfo::isPlaySession)
                    .forEach(sessionInfo -> sendVideoEvent(sessionInfo, sessionInfo.isNewSession() ? "video_start" : "video_progress", null));
        }
    }
}
