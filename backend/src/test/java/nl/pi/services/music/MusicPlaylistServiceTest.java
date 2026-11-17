package nl.pi.services.music;

import nl.pi.config.MusicProperties;
import nl.pi.repositories.InvalidAlbumPathException;
import nl.pi.repositories.InvalidPlaylistException;
import nl.pi.repositories.MusicFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MusicPlaylistServiceTest {

    @TempDir
    Path root;
    Path album;
    MusicPlaylistService service;

    @BeforeEach
    void setUp() throws IOException {
        album = Files.createDirectories(root.resolve("Artist/Album"));
        Files.writeString(album.resolve("a.mp3"), "a");
        Files.writeString(album.resolve("z.FLAC"), "z");
        service = service(root);
    }

    private MusicPlaylistService service(Path directory) {
        return new MusicPlaylistService(new MusicFileRepository(new MusicProperties(directory)));
    }

    private void playlist(String text) throws IOException {
        Files.writeString(album.resolve("Lijst.M3U"), text);
    }

    @Test
    void readsTheActualPlaylistPreservingOrderDuplicatesAndExtinf() throws IOException {
        Files.createDirectory(album.resolve("disc"));
        Files.writeString(album.resolve("disc/é +%&#?.mp3"), "special");
        playlist("\uFEFF#EXTM3U\r\n\r\n# Comment\r\n#EXTINF:123.5,Artiest - Titel\r\n"
                + "z.FLAC\r\n#EXTINF:-1,Tweede\r\ndisc\\é +%&#?.mp3\r\na.mp3\r\nz.FLAC\r\n");

        var result = service.getPlaylist("Artist/Album").orElseThrow();

        assertEquals("Lijst.M3U", result.filename());
        assertEquals(List.of(
                new MusicPlaylist.Entry("#EXTINF:123.5,Artiest - Titel", "Artist/Album/z.FLAC"),
                new MusicPlaylist.Entry("#EXTINF:-1,Tweede", "Artist/Album/disc/é +%&#?.mp3"),
                new MusicPlaylist.Entry(null, "Artist/Album/a.mp3"),
                new MusicPlaylist.Entry(null, "Artist/Album/z.FLAC")), result.entries());
    }

    @Test
    void supportsParentReferencesWithinTheRootAndLiteralPercentEscapes() throws IOException {
        Files.writeString(root.resolve("Artist/shared.mp3"), "shared");
        Files.writeString(album.resolve("%2e%2e.mp3"), "literal");
        playlist("../shared.mp3\n%2e%2e.mp3\n");
        assertEquals(List.of("Artist/shared.mp3", "Artist/Album/%2e%2e.mp3"),
                service.getPlaylist("Artist/Album").orElseThrow().entries().stream()
                        .map(MusicPlaylist.Entry::trackPath).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\uFEFF#EXTM3U\n\n", "#EXTINF:10,Orphan", "#EXTINF:bad,title\na.mp3",
            "#EXTINF:1,One\n#EXTINF:2,Two\na.mp3", "missing.mp3", "cover.jpg",
            "https://example.org/a.mp3", "file:///tmp/a.mp3", "//server/a.mp3",
            "/tmp/a.mp3", "C:\\music\\a.mp3", "C:a.mp3", "../../../outside.mp3",
            "a\0.mp3", "#EXT-X-STREAM-INF:BANDWIDTH=1\na.mp3", "a.mp3\nmissing.mp3"})
    void rejectsInvalidContentWithoutSkippingEntries(String text) throws IOException {
        playlist(text);
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("Artist/Album"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", ".", "/", "/Artist/Album", "../outside", "a/../../outside",
            "invalid\0", "C:\\Music", "\\\\server\\music", "1/2/3/4/5"})
    void rejectsInvalidAlbumPaths(String path) {
        assertThrows(InvalidAlbumPathException.class, () -> service.getPlaylist(path));
    }

    @Test
    void acceptsTheEmptyAlbumPathEmittedForTracksInTheMusicRoot() throws IOException {
        Files.writeString(root.resolve("root.mp3"), "root");
        Files.writeString(root.resolve("root.m3u"), "#EXTM3U\nroot.mp3\nArtist/Album/a.mp3");
        assertEquals(List.of("root.mp3", "Artist/Album/a.mp3"),
                service.getPlaylist("").orElseThrow().entries().stream()
                        .map(MusicPlaylist.Entry::trackPath).toList());
    }

    @Test
    void requiresExactlyOnePlaylistDirectlyInsideTheAlbum() throws IOException {
        assertTrue(service.getPlaylist("missing").isEmpty());
        assertTrue(service.getPlaylist("Artist/Album").isEmpty());
        Files.createDirectory(album.resolve("nested"));
        Files.writeString(album.resolve("nested/child.m3u"), "../a.mp3");
        assertTrue(service.getPlaylist("Artist/Album").isEmpty());
        playlist("a.mp3");
        Files.writeString(album.resolve("other.m3u"), "z.FLAC");
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("Artist/Album"));
    }

    @Test
    void rejectsNonRegularAndMalformedUtf8Playlists() throws IOException {
        var file = album.resolve("bad.m3u");
        Files.createDirectory(file);
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("Artist/Album"));
        Files.delete(file);
        Files.write(file, new byte[]{(byte) 0xc3, 0x28});
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("Artist/Album"));
    }

    @Test
    void rejectsSymlinkAlbumsPlaylistsTracksAndDirectories(@TempDir Path outside) throws IOException {
        playlist("a.mp3");
        Files.createSymbolicLink(root.resolve("alias"), album);
        Files.createSymbolicLink(root.resolve("broken"), outside.resolve("missing"));
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("alias"));
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("alias/../Artist/Album"));
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("broken"));
        Files.delete(album.resolve("Lijst.M3U"));
        var source = Files.writeString(outside.resolve("source"), "a.mp3");
        Files.createSymbolicLink(album.resolve("Lijst.M3U"), source);
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("Artist/Album"));
        Files.delete(album.resolve("Lijst.M3U"));
        Files.createSymbolicLink(album.resolve("link.mp3"), album.resolve("a.mp3"));
        Files.createSymbolicLink(album.resolve("external.mp3"), outside.resolve("absent"));
        Files.createSymbolicLink(album.resolve("disc"), album);
        for (var entry : List.of("link.mp3", "external.mp3", "disc/a.mp3", "disc/../a.mp3")) {
            playlist(entry);
            assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("Artist/Album"));
        }
    }

    @Test
    void supportsASymlinkRootButReportsMissingBrokenOrNonDirectoryRootsAsIoFailures() throws IOException {
        playlist("a.mp3");
        var alias = Files.createSymbolicLink(root.resolve("root-alias"), root);
        assertEquals(1, service(alias).getPlaylist("Artist/Album").orElseThrow().entries().size());
        var broken = Files.createSymbolicLink(root.resolve("broken-root"), root.resolve("missing"));
        for (var directory : List.of(root.resolve("missing"), broken, album.resolve("a.mp3"))) {
            assertThrows(IOException.class, () -> service(directory).getPlaylist("Artist/Album"));
        }
    }

    @Test
    void enforcesLibraryDepthForTracksAndAllowsTheLastValidPlaylistDepth() throws IOException {
        var deep = Files.createDirectories(root.resolve("1/2/3/4/5"));
        Files.writeString(deep.resolve("too-deep.mp3"), "deep");
        playlist("../../1/2/3/4/5/too-deep.mp3");
        assertThrows(InvalidPlaylistException.class, () -> service.getPlaylist("Artist/Album"));
        Files.writeString(deep.getParent().resolve("valid.mp3"), "valid");
        Files.writeString(deep.getParent().resolve("valid.m3u"), "valid.mp3");
        assertEquals(1, service.getPlaylist("1/2/3/4").orElseThrow().entries().size());
    }
}
