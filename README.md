# Wowza Google Analytics
The **Analytics** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) enables you to track your streaming connections in the Google Analytics service.

## Prerequisites
Wowza Streaming Engine™ 4.8.22 or later is required.
Java 11 with the `java.net.http` module included.

You'll need your Google tracking ID for the domain (website property) being tracked in Google Analytics. The tracking ID is a string like **G-XXXXXXXXXX**.

## Usage
Specify your Google tracking ID and Wowza Streaming Engine will report all _video_start_, _video_progress_, and _video_complete_ events using the existing GA4 video events. Two custom events are added to optionally track _video_publish_ and _video_unpublish_ events.  Extra parameters are added over and above the default parameters to specifically track _video_type_ (live or vod), _video_rendition_, _video_protocol_, and _video_live_progress_.  For full reporting, these custom events and parameters will need to be added to your GA4 property.

The module is also able to send tracking events via a _Server Side Tag Manager_ instance for further processing before forwarding to GA4.

## More resources
To use the compiled version of this module, see [Send connection and stream statistics to Google Analytics with a Wowza Streaming Engine Java module](https://www.wowza.com/docs/how-to-send-connection-and-stream-statistics-to-google-analytics-analytics).

[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/serverapi/)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/docs/how-to-extend-wowza-streaming-engine-using-the-wowza-ide)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/developer) to learn more about our APIs and SDK.

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](/LICENSE.txt).
