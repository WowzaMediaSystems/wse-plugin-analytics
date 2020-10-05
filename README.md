# Wowza Google Analytics
The **Analytics** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) enables you to track your streaming connections in the Google Analytics service.

This repo includes a [compiled version](/lib/wse-plugin-analytics.jar).

## Prerequisites
Wowza Streaming Engine™ 4.0.0 or later is required.

You'll need your Google tracking ID for the domain (website property) being tracked in Google Analytics. The tracking ID is a string like **UA-000000-01**.

## Usage
Specify your Google tracking ID and Wowza Streaming Engine will report all play/stop/publish/unpublish events along with the client IP location and protocol in use.

## More resources
To use the compiled version of this module, see [Send connection and stream statistics to Google Analytics with a Wowza Streaming Engine Java module](https://www.wowza.com/docs/how-to-send-connection-and-stream-statistics-to-google-analytics-analytics).

[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/serverapi/)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/docs/how-to-extend-wowza-streaming-engine-using-the-wowza-ide)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/developer) to learn more about our APIs and SDK.

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](/LICENSE.txt).
