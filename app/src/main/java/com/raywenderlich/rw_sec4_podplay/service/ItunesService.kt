package com.raywenderlich.rw_sec4_podplay.service

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesService {

    /*
    This is your first encounter with a Retrofit annotation. Annotations always start with the @ symbol.
    This annotation is a “function” annotation, meaning that it applies to a function.
    Retrofit defines several function annotations that represent standard HTTP requests such as GET, POST, and PUT.
    The @GET annotation takes a single parameter: The path of the endpoint that should be called.
    The annotation applies to the function that immediately follows.
    */
    @GET("/search?media=podcast")

    /*
    The method searchPodcastByTerm is a suspending method and takes a single parameter that has a Retrofit @Query annotation.
    This annotation tells Retrofit that this parameter should be added as a query term in the path defined by the @GET annotation.
    The annotation takes a single parameter that represents the name of the query term.
    The return type is a retrofit Response class that will let you know if the request was successful.
    When you call searchPodcastByTerm(), it must be called in a coroutine method and will run asynchronously to return a Response object containing the PodcastResponse.
    As an example, calling searchPodcastByTerm(“Android Developer”) results in Retrofit using a final URL of /search?media=podcast&term=Android+Developer.
    Retrofit automatically URL-Encodes the parameter names and values when constructing the URL.
    * */
    suspend fun searchPodcastByTerm(@Query("term") term: String): Response<PodcastResponse>

    //You define a companion object in the ItunesService interface.
    companion object {

        /*
        The instance property of the companion object holds the only application-wide instance of the ItunesService.
        This property looks a little different than the ones you’ve defined in the past — and for good reason.
        This definition allows the instance property to return a Singleton object. When the application needs to use ItunesService, it simply references ItunesService.instance.
        Singleton objects are objects that have a single instance for the lifetime of the application.
        No matter how many times the instance property is accessed, it only performs the initialization one time and will always return the same ItunesService object.
        This is accomplished by using a Kotlin concept known as PROPERTY DELEGATION.
        * */
        val instance: ItunesService by lazy {
            // 5
            val retrofit = Retrofit.Builder()
                .baseUrl("https://itunes.apple.com")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            // 6
            retrofit.create(ItunesService::class.java)
        }
    }
}
