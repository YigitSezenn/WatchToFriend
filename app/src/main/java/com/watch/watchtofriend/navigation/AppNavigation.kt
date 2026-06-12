package com.watch.watchtofriend.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object CreateRoom : Screen("create_room")
    object JoinRoom : Screen("join_room?code={code}") {
        const val routeBase = "join_room"
        fun withOptionalCode(code: String? = null): String =
            if (code.isNullOrBlank()) routeBase else "join_room?code=${android.net.Uri.encode(code)}"
    }
    object WatchRoom : Screen("watch_room/{roomId}") {
        fun withArgs(roomId: String) = "watch_room/$roomId"
    }
    object DmScreen : Screen("dm/{dmId}/{otherName}") {
        fun withArgs(dmId: String, otherName: String) =
            "dm/${android.net.Uri.encode(dmId)}/${android.net.Uri.encode(otherName)}"
    }
    object Admin : Screen("admin")
}
