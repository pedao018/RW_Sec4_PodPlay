package com.raywenderlich.rw_sec4_podplay.service

import android.util.Log
import com.raywenderlich.rw_sec4_podplay.BuildConfig
import com.raywenderlich.rw_sec4_podplay.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.w3c.dom.Node
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class RssFeedService private constructor() {

    companion object {
        val instance: RssFeedService by lazy {
            RssFeedService()
        }
    }

    suspend fun getFeed(xmlFileURL: String): RssFeedResponse? {
        val service: FeedService

        /*
        You use HttpLoggingInterceptor in order to log events around the request.
        You wouldn’t want these logs to be produced in production, so this interceptor is only added for debug builds.
        **/
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient().newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            client.addInterceptor(interceptor)
        }
        client.build()

        /*
        One problem we have at this point is that the feed URL doesn’t end in a trailing slash “/” but that’s required for Retrofit in the .baseURL() call.
        So you add it here. If you just put .baseUrl("$xmlFileURL/") here, it would work, for most cases.
        But some podcast feed URLs are a bit “special” and would still fail, because of the formatting of the URL, which Retrofit might not like.
        For example, for ATP (Accidental Tech Podcast) the URL formatting looks like this: https://atp.fm/episodes?format=rss.
        In this case, we want to get rid of the parameters (everything after the ?).
        So we split the url until the first param and that will be the base url.
        In the case of ATP, then we get https://atp.fm/episodes/.
        * */
        val retrofit = Retrofit.Builder()
            .baseUrl("${xmlFileURL.split("?")[0]}/")
            .build()
        service = retrofit.create(FeedService::class.java)

        try {
            val result = service.getFeed(xmlFileURL)
            if (result.code() >= 400) {
                println("server error, ${result.code()}, ${result.errorBody()}")
                return null
            } else {
                var rssFeedResponse: RssFeedResponse? = null

                /*
                DocumentBuilderFactory provides a factory that can be used to obtain a parser for XML documents.
                DocumentBuilderFactory.newInstance() creates a new document builder named dBuilder.
                dBuilder.parse() is called with the RSS file content stream and the resulting top-level XML Document is assigned to doc.

                The parse() function is thread blocking, so it needs to be dispatched properly in a thread-safe manner using coroutines.
                Note we use IO dispatcher here rather than the default dispatcher.
                IO dispatcher allocates additional threads on top of the ones allocated to the default dispatcher,
                so we can do blocking IO and fully utilize the machine’s CPU resources at the same time.
                * */
                val dbFactory = DocumentBuilderFactory.newInstance()
                val dBuilder = dbFactory.newDocumentBuilder()
                withContext(Dispatchers.IO) {
                    val doc = dBuilder.parse(result.body()?.byteStream())
                    val rss = RssFeedResponse(episodes = mutableListOf())
                    domToRssFeedResponse(doc, rss)
                    rssFeedResponse = rss
                    //Log.e("haha", rssFeedResponse.toString())
                }

                return rssFeedResponse
            }
        } catch (t: Throwable) {
            println("error, ${t.localizedMessage}")
        }
        return null
    }

    private fun domToRssFeedResponse(node: Node, rssFeedResponse: RssFeedResponse) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val nodeName = node.nodeName
            val parentName = node.parentNode.nodeName

            val grandParentName = node.parentNode.parentNode?.nodeName ?: ""
            if (parentName == "item" && grandParentName == "channel") {
                // 3
                val currentItem = rssFeedResponse.episodes?.last()
                if (currentItem != null) {
                    // 4
                    when (nodeName) {
                        "title" -> currentItem.title = node.textContent
                        "description" -> currentItem.description = node.textContent
                        "itunes:duration" -> currentItem.duration = node.textContent
                        "guid" -> currentItem.guid = node.textContent
                        "pubDate" -> currentItem.pubDate = node.textContent
                        "link" -> currentItem.link = node.textContent
                        "enclosure" -> {
                            currentItem.url = node.attributes.getNamedItem("url")
                                .textContent
                            currentItem.type = node.attributes.getNamedItem("type")
                                .textContent
                        }
                    }
                }
            }

            if (parentName == "channel") {
                when (nodeName) {
                    "title" -> rssFeedResponse.title = node.textContent
                    "description" -> rssFeedResponse.description = node.textContent
                    "itunes:summary" -> rssFeedResponse.summary = node.textContent
                    "item" -> rssFeedResponse.episodes?.add(RssFeedResponse.EpisodeResponse())
                    "pubDate" -> rssFeedResponse.lastUpdated =
                        DateUtils.xmlDateToDate(node.textContent)
                }
            }
        }
        val nodeList = node.childNodes
        for (i in 0 until nodeList.length) {
            val childNode = nodeList.item(i)
            domToRssFeedResponse(childNode, rssFeedResponse)
        }
    }
}

interface FeedService {
    @Headers(
        "Content-Type: application/xml; charset=utf-8",
        "Accept: application/xml"
    )
    @GET
    suspend fun getFeed(@Url xmlFileURL: String): Response<ResponseBody>
}
