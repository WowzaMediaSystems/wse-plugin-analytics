package com.wowza.wms.plugin.analytics.model;

import java.util.*;

public class VideoEvent
{
    private final String eventType;
    private final String title;
    private final String url;
    private final String protocol;
    private final String videoType;
    private String provider = "Wowza Streaming Engine";
    private String rendition = null;
    private double currentTime = -1;
    private double duration = -1;
    private int vodPercent = -1;
    private int liveProgress = -1;

    public VideoEvent(String eventType, String title, String url, String protocol, String videoType)
    {
        this.eventType = eventType;
        this.title = title;
        this.url = url;
        this.protocol = protocol;
        this.videoType = videoType;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public void setCurrentTime(double currentTime)
    {
        this.currentTime = currentTime;
    }

    public void setDuration(double duration)
    {
        this.duration = duration;
    }

    public void setVodPercent(int vodPercent)
    {
        this.vodPercent = vodPercent;
    }

    public void setLiveProgress(int liveProgress)
    {
        this.liveProgress = liveProgress;
    }

    public void setRendition(String rendition)
    {
        this.rendition = rendition;
    }

    public String getEventType()
    {
        return eventType;
    }

    public Map<String, Object> getGAEventParams()
    {
        Map<String, Object> params = new HashMap<>();
        params.put("video_title", title);
        params.put("video_provider", provider);
        params.put("video_protocol", protocol);
        params.put("video_type", videoType);
        if (url != null)
            params.put("video_url", url);
        if (rendition != null)
            params.put("video_rendition", rendition);
        if (currentTime >= 0)
            params.put("video_current_time", currentTime);
        if (duration >= 0)
            params.put("video_duration", duration);
        if (vodPercent >= 0)
            params.put("video_percent", vodPercent);
        if (liveProgress >= 0)
            params.put("video_live_progress", liveProgress);
        return params;
    }

    @Override
    public String toString()
    {
        return "VideoEvent{" +
                "eventType='" + eventType + '\'' +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", protocol='" + protocol + '\'' +
                ", videoType='" + videoType + '\'' +
                ", provider='" + provider + '\'' +
                (rendition != null ? ", rendition='" + rendition + '\'' : "") +
                (currentTime >= 0 ? ", currentTime=" + currentTime : "") +
                (duration >= 0 ? ", duration=" + duration : "") +
                (vodPercent >= 0 ? ", percent=" + vodPercent : "") +
                (liveProgress >= 0 ? ", liveProgress=" + liveProgress : "") +
                '}';
    }
}
