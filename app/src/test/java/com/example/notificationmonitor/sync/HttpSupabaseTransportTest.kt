package com.example.notificationmonitor.sync

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

class HttpSupabaseTransportTest {

    @Test
    fun postsJsonWithSupabaseHeaders() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var method = ""
        var apiKey = ""
        var authorization = ""
        var contentType = ""
        var prefer = ""
        var path = ""
        var body = ""
        server.createContext("/rest/v1/notifications") { exchange ->
            method = exchange.requestMethod
            path = exchange.requestURI.rawPath
            apiKey = exchange.requestHeaders.getFirst("apikey").orEmpty()
            authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            contentType = exchange.requestHeaders.getFirst("Content-Type").orEmpty()
            prefer = exchange.requestHeaders.getFirst("Prefer").orEmpty()
            body = exchange.requestBody.bufferedReader().readText()
            val response = "{}".toByteArray()
            exchange.sendResponseHeaders(201, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val port = server.address.port
            val response = HttpSupabaseTransport().post(
                url = "http://127.0.0.1:$port/rest/v1/notifications",
                apiKey = "anon-key",
                bearerToken = "user-access-token",
                body = """{"title":"hi"}""",
                preferMinimal = true
            )
            assertEquals(201, response.statusCode)
            assertEquals("POST", method)
            assertEquals("/rest/v1/notifications", path)
            assertEquals("anon-key", apiKey)
            assertEquals("Bearer user-access-token", authorization)
            assertTrue(contentType.startsWith("application/json"))
            assertEquals("return=minimal", prefer)
            assertEquals("""{"title":"hi"}""", body)
        } finally {
            server.stop(0)
        }
    }
}
