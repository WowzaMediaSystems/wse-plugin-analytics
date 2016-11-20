/*
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import com.wowza.util.MD5DigestUtils;

public class GoogleAnalytics
{
	Random UTMN = new Random();
	Random cookie = new Random();
	Random ranPointer = new Random();
	DateFormat dateFormat = new SimpleDateFormat("EEE, FF-MMM-yyyy HH:mm:ss");

	long myTime = 0L;
	String appName = "";
	String urchinCode = "";
	String gaHost = "";
	String GAPrefix = "";
	String gaDomain = "";

	public GoogleAnalytics(String appname, String urchin, String domain, String host, String prefix)
	{
		appName = appname;
		urchinCode = urchin;
		gaDomain = domain;
		gaHost = host;
		GAPrefix = prefix;
	}

	public String makeReferrer(String referrer)
	{
		String localReferrer = "";

		if (referrer.length() > 0)
			localReferrer = "%2F" + GAPrefix + "%2F" + appName + "%2F" + referrer;
		return localReferrer;
	}

	public String makeVisitorID(String sessionID, String previousCookie)
	{
		return getVisitorId(sessionID, urchinCode, "WowzaStreamingEngine", previousCookie);
	}

	public String makeGoogleCookie(String visitorID)
	{
		long nowTime = new Date().getTime();
		nowTime = nowTime + 86400;
		String expire = dateFormat.format(nowTime);
		return "__utmmobile=" + visitorID + "; Expires=" + expire + "; Path=/";
	}

	public String makeGARequest(String stream, String referrer, String ipaddress, String visitorID, String eventName, String eventType, String sessionID)
	{
		myTime = System.currentTimeMillis() / 1000;
		String utmcc = "__utma%3D" + sessionID + "." + sessionID + "." + sessionID + "." + sessionID + "." + sessionID + "." + sessionID + "%3B";

		String eventCaller = "http://www.google-analytics.com/__utm.gif?utmdt=" + stream + "&utmt=event&utme=5(" + eventName + "*" + eventType + "*" + stream + ")&utmwv=4.4sh" + "&utmn=" + generateUTMN() + "&utmhn=" + gaDomain + "&utmr=" + referrer + "&utmp=%2F" + GAPrefix + "%2F" + appName + "%2F"
				+ stream + "&utmac=" + urchinCode + "&utmcc=" + utmcc + "&utmvid=" + visitorID + "&utmip=" + returnGAIP(ipaddress) + "&uip=" + returnGAIP(ipaddress);

		return eventCaller;
	}

	public String returnGAIP(String ipaddress)
	{
		String output = "";

		try
		{
			String[] iPList = ipaddress.split("\\.");
			output = iPList[0] + "." + iPList[1] + "." + iPList[2] + ".0";
		}
		catch (Exception e)
		{
			output = "0.0.0.0";
		}

		return output;
	}

	public String generateSmallRandom()
	{
		return String.valueOf(ranPointer.nextInt(1000));
	}

	public String generateRanPointer()
	{
		int baseNumber = 1000000000;
		int range = 1147483647;
		int ran = ranPointer.nextInt(range);
		ran = ran + baseNumber;
		return String.valueOf(ran);
	}

	public String generateCookie()
	{
		int baseNumber = 10000000;
		int range = 89999999;
		int cookie = this.cookie.nextInt(range);
		cookie = cookie + baseNumber;
		return String.valueOf(cookie);
	}

	public String generateUTMN()
	{
		int baseNumber = 1000000000;
		double range = 8999999999D;
		double gutmn = UTMN.nextDouble();
		gutmn = gutmn * range;
		gutmn = gutmn + baseNumber;
		String output = String.format("%20.0f", gutmn);
		output = output.replace(" ", "");
		return output;
	}

	public String getVisitorId(String guid, String account, String userAgent, String cookie)
	{
		if (cookie.length() != 0)
			return cookie;

		String message = "";
		if (guid.length() != 0)
			message = guid + account;
		else
			message = userAgent + System.currentTimeMillis() + System.nanoTime();

		String md5String = crcCheck(message);
		if (md5String.length() > 16)
			md5String = md5String.substring(0, 16);

		return md5String;
	}

	public String crcCheck(String data)
	{
		return MD5DigestUtils.generateHash(data);
	}

}
