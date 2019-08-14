package com.geely.gic.hmi.http

import com.geely.gic.hmi.Users
import com.geely.gic.hmi.data.dao.DAOFacade
import com.geely.gic.hmi.data.model.User
import com.geely.gic.hmi.http.security.hashFunction
import com.geely.gic.hmi.http.utils.logger
import com.geely.gic.hmi.http.utils.redirect
import com.geely.gic.hmi.http.utils.request
import com.geely.gic.hmi.http.utils.respond
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.log
import io.ktor.auth.authenticate
import io.ktor.http.Parameters
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.routing.Route

fun Route.userInfo(dao: DAOFacade) {
    get<Users.UserInfo> {
        logger.info("GET: {}", call.request(it))

        val user = it.user
        val userInfo = dao.user(user)
        if (userInfo == null) {
            val error = Users.UserInfo(user)
            call.redirect(error.copy(error = "User ${it.user} doesn't exist"))
        } else {
            call.respond(userInfo)
        }
    }

    post<Users.UserUpdate> {
        val post = call.receive<Parameters>()

        val userId = post["userId"] ?: return@post call.redirect(it.copy(error = "Invalid userId"))
        val password = post["password"] ?: return@post call.redirect(it.copy(error = "Invalid password"))
        val newPassword = post["newPassword"]
        val email = post["email"]
        val displayName = post["displayName"]

        val error = Users.UserUpdate(userId = userId)

        when {
            newPassword != null && newPassword.length < 6 -> return@post call.redirect(error.copy(error = "NewPassword should be at least 6 characters long"))
            email != null && dao.userByEmail(email) != null -> return@post call.redirect(error.copy(error = "User with the following email $email is already registered"))
            else -> {
                val user = dao.user(userId)
                    ?: return@post call.redirect(error.copy(error = "User with the following login is already registered"))

                val hash = if (newPassword != null) hashFunction(newPassword) else hashFunction(password)

                val newUser = User(
                    userId = userId,
                    email = email ?: user.email,
                    displayName = displayName ?: user.displayName,
                    passwordHash = hash
                )
                try {
                    dao.updateUser(newUser)
                } catch (e: Throwable) {
                    application.log.error("Failed to update user", e)
                    return@post call.redirect(error.copy(error = "Failed to update"))
                }

                val result = "OK"
                call.respond(result)
            }
        }
    }
}