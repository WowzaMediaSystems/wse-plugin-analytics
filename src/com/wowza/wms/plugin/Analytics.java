package com.wowza.wms.plugin;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.wowza.util.StringUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.client.IClient;
import com.wowza.wms.httpstreamer.model.IHTTPStreamerSession;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.mediacaster.IMediaCaster;
import com.wowza.wms.mediacaster.MediaCasterNotifyBase;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.plugin.GoogleAnalytics;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.rtp.model.RTPStream;
import com.wowza.wms.rtp.model.RTSPActionNotifyBase;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;
import com.wowza.wms.server.Server;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.MediaStreamActionNotifyBase;

public class Analytics extends ModuleBase
{
	class MediaCasterListener extends MediaCasterNotifyBase
	{
		@Override
		public void onStreamStart(IMediaCaster mediaCaster)
		{
			sendNotification(mediaCaster, "publish");
		}

		@Override
		public void onStreamStop(IMediaCaster mediaCaster)
		{
			sendNotification(mediaCaster, "unpublish");
		}

		private void sendNotification(IMediaCaster mediaCaster, String event)
		{
			IMediaStream stream = mediaCaster.getStream();
			String streamName = stream.getName();
			String ip = "";
			String clientId = "";
			IApplicationInstance appInstance = null;
			WMSProperties properties = null;

			String protocol = "";
			if (stream.getRTPStream() != null)
			{
				RTPSession session = stream.getRTPStream().getSession();
				ip = session.getIp();
				clientId = session.getSessionId();
				properties = session.getProperties();
				appInstance = session.getAppInstance();
				protocol = "rtp";
			}
			else if (stream.getClient() != null)
			{
				IClient client = stream.getClient();
				ip = client.getIp();
				clientId = String.valueOf(client.getClientId());
				properties = client.getProperties();
				appInstance = client.getAppInstance();
				protocol = "rtmp";
			}
			else if (stream.getHTTPStreamerSession() != null)
			{
				IHTTPStreamerSession session = stream.getHTTPStreamerSession();
				ip = session.getIpAddress();
				clientId = session.getSessionId();
				properties = session.getProperties();
				appInstance = session.getAppInstance();
				protocol = getHTTPProtocol(session);
			}
			else
			{
				// handle liverepeater
				ip = "127.0.0.1";
				clientId = String.valueOf(mediaCaster.getMediaCasterStreamItem().getUniqueId());
				properties = stream.getProperties();
				appInstance = mediaCaster.getAppInstance();

				protocol = "rtmp";
				getLogger().info(MODULE_NAME + " ****** sendNotification()::clientId : " + clientId);
				getLogger().info(MODULE_NAME + " ****** sendNotification()::mediaCaster.getMediaCasterDef().getName : " + mediaCaster.getMediaCasterDef().getName());
			}

			if (appInstance != null && properties != null)
			{
				String cookie = sendStatsToGoogle(clientId, properties.getPropertyStr(PROP_GA_COOKIE, ""), streamName, "", ip, event, protocol);
				if (cookie != null)
					properties.setProperty(PROP_GA_COOKIE, cookie);
				sendStatsToRemoteServer(streamName, appInstance);
			}
		}
	}

	class StreamListener extends MediaStreamActionNotifyBase
	{
		@Override
		public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset)
		{
			if (moduleDebug)
				getLogger().info(MODULE_NAME + " onPlay()::Stream Name: " + streamName);

			IClient client = stream.getClient();
			if (client != null)
			{
				String cookie = sendStatsToGoogle(String.valueOf(client.getClientId()), client.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), streamName, "", client.getIp(), "play", "rtmp");
				if (cookie != null)
					client.getProperties().setProperty(PROP_GA_COOKIE, cookie);
				sendStatsToRemoteServer(streamName, client.getAppInstance());
			}

		}

		@Override
		public void onStop(IMediaStream stream)
		{
			String streamName = stream.getName();
			IClient client = stream.getClient();
			if (client != null)
			{
				String cookie = sendStatsToGoogle(String.valueOf(client.getClientId()), client.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), streamName, "RTMPConnect-" + streamName, client.getIp(), "stop", "rtmp");
				if (cookie != null)
					client.getProperties().setProperty(PROP_GA_COOKIE, "");
				sendStatsToRemoteServer(streamName, client.getAppInstance());
			}
		}

		@Override
		public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
		{
			if (appInstance.getMediaCasterStreams().getMediaCaster(streamName) != null)
				return;

			// default values for server generated streams.
			String sessionType = "internal";
			String sessionId = "internal";
			String ip = "localhost";
			IClient client = stream.getClient();
			if (client != null)
			{
				sessionType = "rtmp";
				sessionId = String.valueOf(client.getClientId());
				ip = client.getIp();
			}
			RTPStream rtpStream = stream.getRTPStream();
			if (rtpStream != null)
			{
				RTPSession rtpSession = rtpStream.getSession();
				if (rtpSession != null)
				{
					sessionType = "rtsp";
					sessionId = rtpSession.getSessionId();
					ip = rtpSession.getIp();
				}
			}
			String cookie = sendStatsToGoogle(sessionId, stream.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), streamName, "", ip, "unpublish", sessionType);
			if (cookie != null)
				// cookie stored in stream properties.
				stream.getProperties().setProperty(PROP_GA_COOKIE, cookie);
			sendStatsToRemoteServer(streamName, appInstance);
		}

		@Override
		public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
		{
			if (appInstance.getMediaCasterStreams().getMediaCaster(streamName) != null)
				return;

			// default values for server generated streams.
			String sessionType = "internal";
			String sessionId = "internal";
			String ip = "localhost";
			IClient client = stream.getClient();
			if (client != null)
			{
				sessionType = "rtmp";
				sessionId = String.valueOf(client.getClientId());
				ip = client.getIp();
			}
			RTPStream rtpStream = stream.getRTPStream();
			if (rtpStream != null)
			{
				RTPSession rtpSession = rtpStream.getSession();
				if (rtpSession != null)
				{
					sessionType = "rtsp";
					sessionId = rtpSession.getSessionId();
					ip = rtpSession.getIp();
				}
			}
			String cookie = sendStatsToGoogle(sessionId, stream.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), streamName, "", ip, "publish", sessionType);
			if (cookie != null)
				// cookie stored in stream properties.
				stream.getProperties().setProperty(PROP_GA_COOKIE, cookie);
			sendStatsToRemoteServer(streamName, appInstance);
		}
	}

	class RTSPListener extends RTSPActionNotifyBase
	{
		@Override
		public void onPlay(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
		{
			String[] streamUrlParts = rtspSession.getUri().split("/");
			if (streamUrlParts != null && streamUrlParts.length > 0)
			{
				String stream = streamUrlParts[streamUrlParts.length - 1];
				String eventName = "play";
				String cookie = sendStatsToGoogle(rtspSession.getSessionId(), rtspSession.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), stream, "RTPConnect", rtspSession.getIp(), eventName, "rtp");
				if (cookie != null)
					rtspSession.getProperties().setProperty(PROP_GA_COOKIE, "");
				sendStatsToRemoteServer(stream, rtspSession.getAppInstance());
			}

		}

		@Override
		public void onTeardown(RTPSession rtpSession, RTSPRequestMessage req, RTSPResponseMessages resp)
		{
			if (rtpSession.getRTSPStream() != null)
				if (rtpSession.getRTSPStream().isModePublish() == false)
				{
					String[] streamUrlParts = rtpSession.getUri().split("/");
					if (streamUrlParts != null && streamUrlParts.length > 0)
					{
						String stream = streamUrlParts[streamUrlParts.length - 1];
						String eventName = "stop";
						String cookie = sendStatsToGoogle(rtpSession.getSessionId(), rtpSession.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), stream, "RTPConnect", rtpSession.getIp(), eventName, "rtp");
						if (cookie != null)
							rtpSession.getProperties().setProperty(PROP_GA_COOKIE, "");
						sendStatsToRemoteServer(stream, rtpSession.getAppInstance());
					}
				}
		}
	}

	class Request implements Runnable
	{
		private WMSLogger requestLogger = null;
//		private HashMap<String, String> requestMap;
		final String url;
		final String cookie;
		final String referer;

		public Request(WMSLogger requestLogger, String url, String referer, String cookie)
		{
			this.requestLogger = requestLogger;
			this.url = url;
			this.cookie = cookie;
			this.referer = referer;
		}

		@Override
		public void run()
		{
			if (!StringUtils.isEmpty(this.url))
			{
				if (moduleDebug)
					this.requestLogger.info(MODULE_NAME + " URL is " + this.url + " Cookie is " + this.cookie + " Referer is " + this.referer);

				int count = 0;
				while (true)
				{

					if (moduleDebug)
						this.requestLogger.info(MODULE_NAME + " This would fire url of " + this.url + " with cookie of " + this.cookie + " count is " + count);
					boolean result = httpEvent(this.url, this.referer, this.cookie, 2000);

					if (result != false)
					{
						this.requestLogger.info(MODULE_NAME + " URL " + this.url + " Success");
						break;
					}
					else
					{
						count++;
						if (count >= wowzalyticsHTTPRetries)
						{
							this.requestLogger.info(MODULE_NAME + " URL " + this.url + " has failed " + count + " times.");
							break;
						}

						try
						{

							if (moduleDebug)
								this.requestLogger.info(MODULE_NAME + " Sleep for " + wowzalyticsDelayForFailedRequests + " milliseconds");
							Thread.sleep(wowzalyticsDelayForFailedRequests);
						}
						catch (InterruptedException e)
						{
							this.requestLogger.error(MODULE_NAME + ".RequestManager.run() InterruptedException", e);
						}
					}
				}
			}
		}

		/*
		 * Function simply handles the remote request.
		 */
		private boolean httpEvent(String url, String referer, String cookie, int timeout)
		{
			Reader in = null;
			try
			{
				URL myUrl = new URL(url); 
				
				URLConnection conn = myUrl.openConnection();
				
				conn.setConnectTimeout(timeout);
				conn.setReadTimeout(timeout);

				conn.setRequestProperty("User-Agent", "WowzaServer");

				if (cookie != null)
					conn.setRequestProperty("Set-Cookie", cookie);

				if (referer != null)
					if (referer.length() > 0)
						conn.setRequestProperty("referer", referer);

				conn.setDoOutput(true);
				StringBuffer buffer = new StringBuffer();
				in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				int ch;
				while ((ch = in.read()) > -1)
					buffer.append((char)ch);
				return true;
			}
			catch (Exception e)
			{
				this.requestLogger.error(MODULE_NAME + ".RequestManager.httpEvent() Exception", e);
				return false;
			}
			finally
			{
				if (in != null)
					try
					{
						in.close();
					}
					catch (IOException e)
					{
						// ignore
					}
			}
		}
	}

	// static constants
	private static String PROP_NAME_PREFIX = "wowzalytics";
	private static String MODULE_NAME = "Wowzalytics";
	private static final String PROP_GA_COOKIE = "googleAnalyticsCOOK";

	//static vars
	private static ThreadPoolExecutor eventRequestThreadPool;
	private static long wowzalyticsDelayForFailedRequests = 1000;
	private static int wowzalyticsHTTPRetries = 5;
	public static boolean serverDebug = false;
	private static WMSProperties serverProps = Server.getInstance().getProperties();

	// instance variables
	private GoogleAnalytics googleAnalytics = null;
	private WMSLogger logger = null;
	private boolean moduleDebug = false;
	private IApplicationInstance appInstance = null;
	private String statsNotificationUrls = null;
	private StreamListener streamListener = new StreamListener();
	private MediaCasterListener mediaCasterListener = new MediaCasterListener();
	private RTSPListener rtspListener = new RTSPListener();

	static
	{
		int analyticThreads = serverProps.getPropertyInt(PROP_NAME_PREFIX + "ThreadPoolSize", 5);
		int analyticIdleTimeout = serverProps.getPropertyInt(PROP_NAME_PREFIX + "ThreadIdleTimeout", 60);
		serverDebug = serverProps.getPropertyBoolean(PROP_NAME_PREFIX + "Debug", false);
		if (WMSLoggerFactory.getLogger(Analytics.class).isDebugEnabled())
			serverDebug = true;

		wowzalyticsDelayForFailedRequests = serverProps.getPropertyLong(PROP_NAME_PREFIX + "DelayForFailedRequests", wowzalyticsDelayForFailedRequests);
		wowzalyticsHTTPRetries = serverProps.getPropertyInt(PROP_NAME_PREFIX + "HTTPMaxRetries", wowzalyticsHTTPRetries);

		eventRequestThreadPool = new ThreadPoolExecutor(analyticThreads, // core size
				analyticThreads, // max size
				analyticIdleTimeout, // idle timeout
				TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					if (serverDebug)
						WMSLoggerFactory.getLogger(getClass()).info(MODULE_NAME + " Runtime.getRuntime().addShutdownHook");
					eventRequestThreadPool.shutdown();
					int threadPoolAwaitTerminationTimeout = serverProps.getPropertyInt(PROP_NAME_PREFIX + "ThreadPoolTerminationTimeout", 5);
					if (!eventRequestThreadPool.awaitTermination(threadPoolAwaitTerminationTimeout, TimeUnit.SECONDS))
						eventRequestThreadPool.shutdownNow();
				}
				catch (InterruptedException e)
				{
					// problem
					WMSLoggerFactory.getLogger(getClass()).error(MODULE_NAME + ".ShutdownHook.run() InterruptedException", e);
				}
			}
		});
	}

	private boolean getPropertyValueBoolean(String key, boolean defaultValue)
	{
		boolean value = serverProps.getPropertyBoolean(key, defaultValue);
		value = this.appInstance.getProperties().getPropertyBoolean(key, value);
		return value;
	}

	private String getPropertyValueStr(String key)
	{
		String value = serverProps.getPropertyStr(key);
		value = this.appInstance.getProperties().getPropertyStr(key, value);
		return value;
	}

	public void onAppStart(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		this.logger = WMSLoggerFactory.getLoggerObj(appInstance);
		this.moduleDebug = getPropertyValueBoolean(PROP_NAME_PREFIX + "Debug", false);

		if (this.logger.isDebugEnabled())
			this.moduleDebug = true;

		if (this.moduleDebug)
			this.logger.info(MODULE_NAME + " DEBUG mode is ON");
		else
			this.logger.info(MODULE_NAME + " DEBUG mode is OFF");

		this.statsNotificationUrls = getPropertyValueStr(PROP_NAME_PREFIX + "StatsNotificationUrls");
		this.googleAnalytics = new GoogleAnalytics(appInstance.getApplication().getName()+"/"+appInstance.getName(), getPropertyValueStr(PROP_NAME_PREFIX + "GACode"), getPropertyValueStr(PROP_NAME_PREFIX + "GADomain"), getPropertyValueStr(PROP_NAME_PREFIX + "GAHost"), getPropertyValueStr(PROP_NAME_PREFIX + "GAPrefix"));
		appInstance.addMediaCasterListener(this.mediaCasterListener);
	}

	/*
	 * When app is shut down we need to ensure existing items in thread can complete then the thread is properly shutdown.
	 * The shutdown method will do just that.
	 */
	public void onAppStop(IApplicationInstance appInstance)
	{
		this.googleAnalytics = null;
	}

	public void onStreamCreate(IMediaStream stream)
	{
		stream.addClientListener(this.streamListener);
	}

	public void onStreamDestroy(IMediaStream stream)
	{
		stream.removeClientListener(this.streamListener);
	}

	public void onRTPSessionCreate(RTPSession rtpSession)
	{
		rtpSession.addActionListener(this.rtspListener);
	}

	public void onRTPSessionDestroy(RTPSession rtpSession)
	{
		rtpSession.removeActionListener(this.rtspListener);
	}

	public String getHTTPProtocol(IHTTPStreamerSession session)
	{
		String connectionProtocol = "HTTP";
		switch (session.getSessionProtocol())
		{
		case IHTTPStreamerSession.SESSIONPROTOCOL_CUPERTINOSTREAMING:
			connectionProtocol = "HTTPCupertino";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_MPEGDASHSTREAMING:
			connectionProtocol = "HTTPMpegDash";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_SMOOTHSTREAMING:
			connectionProtocol = "HTTPSmooth";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_SANJOSESTREAMING:
			connectionProtocol = "HTTPSanjose";
			break;
		}
		return connectionProtocol;
	}

	public void onHTTPSessionCreate(IHTTPStreamerSession session)
	{
		String responseHeader = session.getHTTPHeader("x-playback-session-id");
		if (this.moduleDebug)
			this.logger.info(MODULE_NAME + " onHTTPSessionCreate::stream.getName()::" + session.getStreamName() + ":" + responseHeader);

		String connectionProtocol = getHTTPProtocol(session);

		String cookie = sendStatsToGoogle(session.getSessionId(), session.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), session.getStreamName(), "", session.getIpAddress(), "play", connectionProtocol);
		if (cookie != null)
			session.getProperties().setProperty(PROP_GA_COOKIE, cookie);
		sendStatsToRemoteServer(session.getStreamName(), session.getAppInstance());
	}

	public void onHTTPSessionDestroy(IHTTPStreamerSession session)
	{
		String connectionProtocol = getHTTPProtocol(session); //session.get; //this.getConnectionProtocolStr(session.getSessionProtocol());

		String cookie = sendStatsToGoogle(session.getSessionId(), session.getProperties().getPropertyStr(PROP_GA_COOKIE, ""), session.getStreamName(), connectionProtocol + "Connect", session.getIpAddress(), "stop", connectionProtocol);
		if (cookie != null)
			session.getProperties().setProperty(PROP_GA_COOKIE, "");
		sendStatsToRemoteServer(session.getStreamName(), session.getAppInstance());
	}

	private String sendStatsToGoogle(String sessionId, String cookieStore, String streamName, String referrerStr, String ipAddress, String eventName, String eventValue)
	{

		if (this.googleAnalytics != null)
		{
			String cookieID = this.googleAnalytics.makeVisitorID(sessionId, cookieStore);
			String referer = this.googleAnalytics.makeReferrer(referrerStr);
			String url = this.googleAnalytics.makeGARequest(streamName, referer, ipAddress, cookieID, eventName, eventValue,sessionId);
			eventRequestThreadPool.submit(new Request(this.logger, url, referer, this.googleAnalytics.makeGoogleCookie(cookieID)));
			return cookieID;
		}
		return null;
	}

	private void sendStatsToRemoteServer(String referrer, IApplicationInstance appInstance)
	{
		if (!StringUtils.isEmpty(this.statsNotificationUrls))
		{
			String[] urlArr = this.statsNotificationUrls.split(",");
			if (urlArr.length > 0)
			{
				for (int i = 0; i < urlArr.length; i++)
					eventRequestThreadPool.submit(new Request(this.logger, urlArr[i].trim() + "?streamType=" + referrer, referrer, ""));
			}
		}
	}
}
