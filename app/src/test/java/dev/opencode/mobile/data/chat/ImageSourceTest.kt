package dev.opencode.mobile.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a picture in a reply actually lives.
 *
 * A model writes a path on the machine running the server, so the app has to
 * turn it into a request for the server's own copy. Getting the path wrong
 * means a broken image with no explanation, so the shapes are pinned here.
 */
class ImageSourceTest {

    private val base = "http://192.168.1.20:4096"
    private val project = "/home/me/project"

    private fun remote(src: String, directory: String? = project): String? =
        (ImageSource.resolve(base, directory, src) as? ImageSource.Resolved.Remote)?.url

    @Test
    fun `a relative path is asked for from the server`() {
        assertEquals(
            "http://192.168.1.20:4096/api/fs/read/screens/chat.png?location[directory]=/home/me/project",
            remote("screens/chat.png"),
        )
    }

    @Test
    fun `an absolute path inside the project is shortened`() {
        // The route resolves relative paths against the project, and the model
        // may write either form.
        assertEquals(
            "http://192.168.1.20:4096/api/fs/read/screens/chat.png?location[directory]=/home/me/project",
            remote("/home/me/project/screens/chat.png"),
        )
    }

    @Test
    fun `a path outside the project keeps its own path`() {
        assertEquals(
            "http://192.168.1.20:4096/api/fs/read/tmp/shot.png?location[directory]=/home/me/project",
            remote("/tmp/shot.png"),
        )
    }

    @Test
    fun `a file url names the same file`() {
        assertEquals(
            "http://192.168.1.20:4096/api/fs/read/screens/chat.png?location[directory]=/home/me/project",
            remote("file:///home/me/project/screens/chat.png"),
        )
    }

    @Test
    fun `a windows path is normalised`() {
        // The colon is escaped because it is not legal in a path segment, and
        // the server decodes the parameter before using it.
        assertEquals(
            "http://192.168.1.20:4096/api/fs/read/screens/chat.png?location[directory]=C%3A/project",
            remote("C:\\project\\screens\\chat.png", directory = "C:/project"),
        )
    }

    @Test
    fun `a remote url is left alone`() {
        assertEquals("https://example.com/a.png", remote("https://example.com/a.png"))
    }

    @Test
    fun `the project directory is encoded`() {
        val url = remote("a.png", directory = "/home/me/my project")
        assertTrue(url!!.contains("location[directory]=/home/me/my%20project"))
    }

    @Test
    fun `the path keeps its separators`() {
        // Encoding the whole path would turn the slashes into %2F, and the
        // server would look for a file with that in its name.
        val url = remote("src/img/a b.png")!!
        assertTrue(url.contains("/api/fs/read/src/img/a%20b.png"))
    }

    @Test
    fun `no directory still asks the server for the path`() {
        assertEquals(
            "http://192.168.1.20:4096/api/fs/read/screens/chat.png",
            remote("screens/chat.png", directory = null),
        )
    }

    @Test
    fun `a trailing slash on the server address is not doubled`() {
        val resolved = ImageSource.resolve("$base/", project, "a.png")
        assertTrue((resolved as ImageSource.Resolved.Remote).url.startsWith("$base/api/fs/read/"))
    }

    // ---- inline pictures ---------------------------------------------------

    @Test
    fun `a data uri is decoded rather than fetched`() {
        // Coil does not decode data: URIs, so the bytes are pulled out here.
        // This is how an image the user attached comes back from the server.
        val resolved = ImageSource.resolve(base, project, "data:image/png;base64,QUJD")

        assertTrue(resolved is ImageSource.Resolved.Inline)
        assertEquals("ABC", String((resolved as ImageSource.Resolved.Inline).bytes))
    }

    @Test
    fun `a data uri with a charset still decodes`() {
        val resolved = ImageSource.resolve(base, project, "data:image/png;charset=utf-8;base64,QUJD")
        assertEquals("ABC", String((resolved as ImageSource.Resolved.Inline).bytes))
    }

    @Test
    fun `a malformed data uri resolves to nothing rather than throwing`() {
        assertEquals(null, ImageSource.resolve(base, project, "data:image/png;base64,!!!!"))
    }

    @Test
    fun `an empty source resolves to nothing`() {
        assertEquals(null, ImageSource.resolve(base, project, "   "))
    }
}
