package com.geely.gic.hmi

import com.geely.gic.hmi.data.model.IndexData
import com.geely.gic.hmi.data.model.Session
import com.geely.gic.hmi.route.user
import com.geely.gic.hmi.utils.copyToSuspend
import com.geely.gic.hmi.utils.expiration
import com.geely.gic.hmi.utils.respondCss
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.freemarker.FreeMarker
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.html.respondHtml
import io.ktor.http.content.*
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.sessions.directorySessionStorage
import io.ktor.sessions.sessions
import io.ktor.util.KtorExperimentalAPI
import kotlinx.css.Color
import kotlinx.css.body
import kotlinx.css.em
import kotlinx.css.p
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.ul
import org.slf4j.LoggerFactory
import java.io.File

//main函数
fun main(args: Array<String>): Unit =
    //创建一个内嵌Netty的服务器
    io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val logger by lazy { LoggerFactory.getLogger(Application::class.java) }

    logger.info("start application")
    val client = HttpClient(Apache) {
    }

//    val htmlContent = client.get<String>("")

    //在 Ktor 中由于解藕的存在，各个功能均是被安装进去的
    //5.安装FreeMarker模板
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }

    //6.安装Session
    install(Sessions) {
        //1).用cookie的方式 将 session 保存在本地
        cookie<Session>("Session", directorySessionStorage(File(".sessions"))) {
            cookie.path = "/"
        }
        //directorySessionStorage() 来自 Ktor 的 Session 库，并且需要注意的是，directorySessionStorage()也是一个 Experimental 的 API，需要加入注解来使其能够顺利编译
        //表示了这个 Session 可以在文件系统里保存，并且作用范围是全站，即以 / 为路径的所有请求。
        // 这意味着我们可以通过请求路径来进行 Session 的隔离。

        //只需要session 不需要保存的情况
//        cookie<Session>("Session")
        //2).另外一种请求方式，即把相关的数据放在 Header
        //通常是用于 API 或 XHR 请求，这个时候我们可以使用 header() 来描述 Session
//        header<Session>("Session") {
//            transform(SessionTransportTransformerMessageAuthentication(SecretKeySpec(key, "HmacSHA256")))
//            //key 是一个 ByteArray 对象，也就是加密用的 key，它可以是任意组合的 byte 串。后面的 HmacSHA256 是采用的算法
//            // Ktor 官方文档内，用于 Header 的 transform 是 SessionTransportTransformerDigest，而这个类并不安全
//            // 为了安全起见，应当使用此处的 SessionTransportTransformerMessageAuthentication 并配合相应的加密手段。
//        }

        //3).上面都两种都是将cookie写入服务端，将前两种结合，就拥有了写到客户端的 Cookie 了  将cookie保存在客户端
//        cookie<Session>("Session") {
//            val secretSignKey = hex("000102030405060708090a0b0c0d0e0f")
//            transform(SessionTransportTransformerMessageAuthentication(secretSignKey))
//        }
    }

    //7.安装webSocket
//    install(WebSockets) {
//        pingPeriod = Duration.ofMinutes(1)
//    }

    //8.安装路由 当我们的路由很多的时候，如果都写在一个文件里，不仅仅文件回变得很大，而且不利于维护和团队协作。所以有了另一个模块 locations
    install(Locations) // 启用 Locations

    //路由代码块 配置路由特性 在代码块中指定路径与HTTP方法定义路由
    routing {
        //1.静态配置  在 get("/") 时，返回 index.html 的内容，而此时并不需要明确的写出 get("/")，只需要写 defaultResource() 即可
        static {
            defaultResource("index.html", "web")
            resources("web")
        }
        //1.实际路径  处理GET请求 和上面的静态配置相同  返回一个html页面
//        get("/") {
//            call.resolveResource("index.html", "web")?.let { index ->
//                call.respond(index)
//            }
//        }
        //2.返回文字
        get("/text") {
            call.respondText { "返回文字" }
        }

        //3.获取携带的参数
        get("/demo") {
            val map = call.parameters.entries()
            logger.info("get /demo 获取参数：{}", map)
            call.respondText { map.toString() }
        }

        get("/html-dsl") {
            call.respondHtml {
                body {
                    h1 { +"HTML" }
                    ul {
                        for (n in 1..10) {
                            li { +"$n" }
                        }
                    }
                }
            }
        }

        get("/styles.css") {
            call.respondCss {
                body {
                    backgroundColor = Color.red
                }
                p {
                    fontSize = 2.em
                }
                rule("p.myclass") {
                    color = Color.blue
                }
            }
        }

        //4.post 请求
        post("/upload") {
            //post 请求获取参数
            val parts = call.receiveMultipart()
            parts.forEachPart {
                when (it) {
                    is PartData.FormItem -> {
                        logger.info("post /upload 获取参数：{}", "${it.name} = ${it.value}")
                    }
                    is PartData.FileItem -> {
                        val file = File("", "file -${System.currentTimeMillis()}")
                        it.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyToSuspend(output)
                            }

                        }
                    }
                }
                it.dispose
            }

        }

        //5.模板返回
        get("/html-freemarker") {
            call.respond(FreeMarkerContent("index.ftl", mapOf("data" to IndexData(listOf(1, 2, 3, 4, 5, 6, 7))), ""))
        }

        //6.Session
        get("/session") {
            val s = call.sessions.get("Session") as? Session
            if (s == null || call.expiration()) {
                //生成session
                call.sessions.set("Session", Session("cookie", "init", System.currentTimeMillis()))
                call.respondText { "generated new session" }
            } else {
                call.respondText { "name: ${s.name}, data: ${s.data}" }
            }
        }


        //7.webSocket 聊天室 chat/Application

        //8.路由的使用
        user()
    }


}

//该代码目前必须和 Application 在一起。
@Location("/user")
class User{
    @Location("/login")
    data class UserLogin(val username: String, val password: String)

    @Location("/register")
    data class UserRegister(val username: String, val password: String)
}





