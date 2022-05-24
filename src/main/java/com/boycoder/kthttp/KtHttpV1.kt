package com.boycoder.kthttp

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Method
import java.lang.reflect.Proxy

data class RepoList(
    var count: Int?,
    var items: List<Repo>?,
    var msg: String?
)

data class Repo(
    var added_stars: String?,
    var avatars: List<String>?,
    var desc: String?,
    var forks: String?,
    var lang: String?,
    var repo: String?,
    var repo_link: String?,
    var stars: String?
)


interface ApiService {
// 假设我们的baseurl是：https://baseurl.com
// 这里拼接结果会是这样：https://baseurl.com/repo
//          ↓
    @GET("/repo")
    fun repos(
        //                Field注解当中的lang，最终会拼接到url当中去
        //            ↓                                                 ↓
        @Field("lang") lang: String,  // https://baseurl.com/repo?lang=Kotlin
        @Field("since") since: String // https://baseurl.com/repo?lang=Kotlin&since=weekly
    ): RepoList
}


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class GET(val value: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Field(val value: String)


object KtHttpV1 {

    // 底层使用 OkHttp
    private var okHttpClient: OkHttpClient = OkHttpClient()

    // 使用 Gson 解析 JSON
    private var gson: Gson = Gson()

    // 这里以baseurl.com为例，实际上我们的KtHttpV1可以请求任意API
    var baseUrl = "https://baseurl.com"

    fun <T> create(service: Class<T>): T {
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf<Class<*>>(service)
            //           ①     ②
            //           ↓      ↓
        ) { _, method, args ->
            // ③
            val annotations = method.annotations
            for (annotation in annotations) {
                // ④
                if (annotation is GET) {
                    // ⑤
                    val url = baseUrl + annotation.value
                    // ⑥
                    return@newProxyInstance invoke(url, method, args!!)
                }
            }
            return@newProxyInstance null

        } as T
    }


    private fun invoke(path: String, method: Method, args: Array<Any>): Any? {
        // 条件判断
        if (method.parameterAnnotations.size != args.size) return null

        // 解析完整的url
        var url = path
        // ①
        val parameterAnnotations = method.parameterAnnotations
        for (i in parameterAnnotations.indices) {
            for (parameterAnnotation in parameterAnnotations[i]) {
                // ②
                if (parameterAnnotation is Field) {
                    val key = parameterAnnotation.value
                    val value = args[i].toString()
                    url += if (!url.contains("?")) {
                        // ③
                        "?$key=$value"
                    } else {
                        // ④
                        "&$key=$value"
                    }

                }
            }
        }
        // 最终的url会是这样：
        // https://baseurl.com/repo?lang=Kotlin&since=weekly

        // 执行网络请求
        val request = Request.Builder()
            .url(url)
            .build()
        val response = okHttpClient.newCall(request).execute()

        // ⑤
        val genericReturnType = method.genericReturnType
        val body = response.body
        val json = body?.string()
        // JSON解析
        val result = gson.fromJson<Any?>(json, genericReturnType)

        // 返回值
        return result
    }
}


fun main() {
    // ①
    val api: ApiService = KtHttpV1.create(ApiService::class.java)

    // ②
    val data: RepoList = api.repos(lang = "Kotlin", since = "weekly")

    println(data)
}