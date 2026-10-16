package voice.core.audiobookshelf.login

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerAddressTest {

  @Test
  fun `a domain is tried with https first`() {
    assertEquals(
      listOf("https://audiobooks.example.com/", "http://audiobooks.example.com/"),
      serverUrlCandidates("audiobooks.example.com"),
    )
  }

  @Test
  fun `a server in the local network is tried with http first`() {
    assertEquals(
      listOf("http://192.168.1.10:13378/", "https://192.168.1.10:13378/"),
      serverUrlCandidates("192.168.1.10:13378"),
    )
  }

  @Test
  fun `a tailscale address counts as local`() {
    assertEquals("http://100.101.102.103/", serverUrlCandidates("100.101.102.103").first())
  }

  @Test
  fun `a typed scheme is kept`() {
    assertEquals(listOf("http://audiobooks.example.com/"), serverUrlCandidates("http://audiobooks.example.com"))
  }

  @Test
  fun `a page of the web app falls back to its folders`() {
    assertEquals(
      listOf(
        "https://example.com/abs/login/",
        "https://example.com/abs/",
        "https://example.com/",
      ),
      serverUrlCandidates("https://example.com/abs/login?redirect=x"),
    )
  }

  @Test
  fun `blank input has no candidates`() {
    assertEquals(emptyList(), serverUrlCandidates("  "))
  }

  @Test
  fun `a local server needs the local network permission`() {
    assertEquals(true, needsLocalNetworkPermission("192.168.1.10:13378"))
    assertEquals(true, needsLocalNetworkPermission("http://nas.local"))
    assertEquals(true, needsLocalNetworkPermission("myserver"))
  }

  @Test
  fun `a public server does not need the local network permission`() {
    assertEquals(false, needsLocalNetworkPermission("audiobooks.example.com"))
    assertEquals(false, needsLocalNetworkPermission("https://abs.example.com/login"))
  }

  @Test
  fun `unparseable input does not need the local network permission`() {
    assertEquals(false, needsLocalNetworkPermission("  "))
  }
}
