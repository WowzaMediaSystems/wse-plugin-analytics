package com.wowza.wms.plugin.analytics.model;

import com.wowza.util.ElapsedTimer;

import java.util.concurrent.atomic.AtomicBoolean;

public class SessionInfo
{
    private final String sessionId;
    private final String ipAddress;
    private final String userAgent;
    private final String gaClientId;
    private final String streamName;
    private final String uri;
    private final String page;
    private final String referer;
    private final String type;
    private final String protocol;
    private final ElapsedTimer elapsedTime;
    private final AtomicBoolean newSession = new AtomicBoolean(true);
    private final AtomicBoolean firstVisit = new AtomicBoolean(true);
    private long lastEventTime = 0;
    private double vodDuration = 0.0;
    private int lastProgress = 0;
    private int[] vodUpdatePercentages = {10, 25, 50, 75};
    private int progressUpdateFrequency = 10;
    private boolean playSession = true;

    public SessionInfo(String sessionId, String ipAddress, String userAgent, String gaClientId, String streamName, String uri, String page, String referer, String type, String protocol, ElapsedTimer elapsedTime)
    {
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.gaClientId = gaClientId;
        this.streamName = streamName;
        this.uri = uri;
        this.page = page;
        this.referer = referer;
        this.type = type;
        this.protocol = protocol;
        this.elapsedTime = elapsedTime;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public String getUserAgent()
    {
        return userAgent;
    }

    public String getGaClientId()
    {
        return gaClientId;
    }

    public String getUri()
    {
        return uri;
    }

    public String getPage()
    {
        return page;
    }

    public String getReferer()
    {
        return referer;
    }

    public String getType()
    {
        return type;
    }

    public String getProtocol()
    {
        return protocol;
    }

    public long getEventElapsedTime()
    {
        long eventElapsedTime = elapsedTime.getTime() - lastEventTime;
        lastEventTime = elapsedTime.getTime();
        return eventElapsedTime;
    }

    public boolean isFirstVisit()
    {
        return firstVisit.getAndSet(false);
    }

    public boolean isNewSession()
    {
        return newSession.getAndSet(false);
    }

    public double getVideoCurrentTime()
    {
        return elapsedTime.getTime() / 1000d;
    }

    public int getVodPercent()
    {
        return Math.min(getVideoCurrentTime() > 0 && vodDuration > 0 ? (int) ((getVideoCurrentTime() / vodDuration) * 100) : 0, 100);
    }

    public int getLastProgress()
    {
        return lastProgress;
    }

    public void setVodDuration(double duration)
    {
        this.vodDuration = duration;
    }

    public double getVodDuration()
    {
        return vodDuration;
    }

    public boolean sendProgressUpdate()
    {
        boolean sendUpdate = false;
        int progress = 0;
        if (type.equals("vod")) //vod progress based on percentage
        {
            int vodPercent = getVodPercent();
            for (int percent : vodUpdatePercentages)
            {
                if (vodPercent >= percent)
                    progress = percent;
            }
        }
        else // live progress based on time
        {
            int updateFrequencySecs = progressUpdateFrequency;
            progress = ((int)getVideoCurrentTime() / updateFrequencySecs) * updateFrequencySecs;
        }
        if (lastProgress != progress)
        {
            lastProgress = progress;
            sendUpdate = true;
        }

        return sendUpdate;
    }

    public void setVodUpdatePercentages(int[] vodUpdatePercentages)
    {
        this.vodUpdatePercentages = vodUpdatePercentages;
    }

    public void setProgressUpdateFrequency(int progressUpdateFrequency)
    {
        this.progressUpdateFrequency = progressUpdateFrequency;
    }

    public String getStreamName()
    {
        return streamName;
    }

    public void setIsPlaySession(boolean playSession)
    {
        this.playSession = playSession;
    }

    public boolean isPlaySession()
    {
        return playSession;
    }
}
