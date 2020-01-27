package com.frolo.muse.di.impl.local;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.frolo.muse.db.AppMediaStore;
import com.frolo.muse.model.media.Album;
import com.frolo.muse.model.media.Artist;
import com.frolo.muse.model.media.Genre;
import com.frolo.muse.model.media.Media;
import com.frolo.muse.model.media.MyFile;
import com.frolo.muse.model.media.Playlist;
import com.frolo.muse.model.media.Song;
import com.frolo.muse.model.media.SongWithPlayCount;

import org.reactivestreams.Publisher;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiPredicate;
import io.reactivex.functions.Function;
import kotlin.Suppress;


final class SongQuery {

    private static final Uri URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

    static final class Sort {
        // Sort orders are case-insensitive

        // For albums, artists and genres only
        static final String BY_DEFAULT = "";

        static final String BY_TITLE =
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        static final String BY_ALBUM =
                MediaStore.Audio.Media.ALBUM + " COLLATE NOCASE ASC";

        static final String BY_ARTIST =
                MediaStore.Audio.Media.ARTIST + " COLLATE NOCASE ASC";

        // For playlist only
        static final String BY_PLAY_ORDER =
                MediaStore.Audio.Playlists.Members.PLAY_ORDER + " ASC";

        static final String BY_DURATION =
                MediaStore.Audio.Media.DURATION + " ASC";

        private Sort() {
        }
    }

    private static class SimpleSong implements Song, Serializable {

        final long id;
        final String source;
        final String title;
        final long albumId;
        final String album;
        final long artistId;
        final String artist;
        final String genre;
        final int duration;
        final int year;

        SimpleSong(
                long id,
                String source,
                String title,
                long albumId,
                String album,
                long artistId,
                String artist,
                String genre,
                int duration,
                int year) {
            this.id = id;
            this.source = source;
            this.title = title != null ? title : "";
            this.albumId = albumId;
            this.album = album != null ? album : "";
            this.artistId = artistId;
            this.artist = artist != null ? artist : "";
            this.genre = genre != null ? genre : "";
            this.duration = duration;
            this.year = year;
        }

        public String getSource() {
            return source;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj != null && obj instanceof SimpleSong) {
                SimpleSong another = (SimpleSong) obj;
                return id == another.id
                        && Objects.equals(source, another.source)
                        && Objects.equals(title, another.title)
                        && albumId == another.albumId
                        && Objects.equals(album, another.album)
                        && artistId == another.artistId
                        && Objects.equals(artist, another.artist)
                        && Objects.equals(genre, another.genre)
                        && duration == another.duration
                        && year == another.year;
            } else return false;
        }

        @Override
        public int hashCode() {
            return (int) getId();
        }

        @Override
        public String toString() {
            return source;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public int getKind() {
            return Media.SONG;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getArtist() {
            return artist;
        }

        @NonNull
        public String getAlbum() {
            return album;
        }

        public long getAlbumId() {
            return albumId;
        }

        public int getDuration() {
            return duration;
        }

        public int getYear() {
            return year;
        }

        @NonNull
        public String getGenre() {
            return genre;
        }

        public long getArtistId() {
            return artistId;
        }
    }

    private static class SongPlayCount {
        final String absolutePath;
        final int playCount;
        final long lastPlayTime;

        SongPlayCount(String absolutePath, int playCount, long lastPlayTime) {
            this.absolutePath = absolutePath;
            this.playCount = playCount;
            this.lastPlayTime = lastPlayTime;
        }
    }

    private static final String[] PROJECTION_SONG = new String[] {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR
    };

    private static final String[] PROJECTION_PLAYLIST_MEMBER = new String[] {
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.DATA,
            MediaStore.Audio.Playlists.Members.TITLE,
            MediaStore.Audio.Playlists.Members.ALBUM_ID,
            MediaStore.Audio.Playlists.Members.ALBUM,
            MediaStore.Audio.Playlists.Members.ARTIST_ID,
            MediaStore.Audio.Playlists.Members.ARTIST,
            MediaStore.Audio.Playlists.Members.DURATION,
            MediaStore.Audio.Playlists.Members.YEAR,
    };

    private static final String[] PROJECTION_SONG_PLAY_COUNT = new String[] {
            AppMediaStore.SongPlayCount.ABSOLUTE_PATH,
            AppMediaStore.SongPlayCount.PLAY_COUNT,
            AppMediaStore.SongPlayCount.LAST_PLAY_TIME
    };

    private static final Query.Builder<Song> BUILDER_SONG =
            new Query.Builder<Song>() {
        @Override
        public Song build(Cursor cursor, String[] projection) {
            return new SimpleSong(
                    cursor.getLong(cursor.getColumnIndex(PROJECTION_SONG[0])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_SONG[1])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_SONG[2])),
                    cursor.getLong(cursor.getColumnIndex(PROJECTION_SONG[3])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_SONG[4])),
                    cursor.getLong(cursor.getColumnIndex(PROJECTION_SONG[5])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_SONG[6])),
                    "",
                    cursor.getInt(cursor.getColumnIndex(PROJECTION_SONG[7])),
                    cursor.getInt(cursor.getColumnIndex(PROJECTION_SONG[8]))
            );
        }
    };

    private static final Query.Builder<Song> BUILDER_PLAYLIST_MEMBER =
            new Query.Builder<Song>() {
        @Override
        public Song build(Cursor cursor, String[] projection) {
            return new SimpleSong(
                    cursor.getLong(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[0])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[1])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[2])),
                    cursor.getLong(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[3])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[4])),
                    cursor.getLong(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[5])),
                    cursor.getString(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[6])),
                    "",
                    cursor.getInt(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[7])),
                    cursor.getInt(cursor.getColumnIndex(PROJECTION_PLAYLIST_MEMBER[8]))
            );
        }
    };

    private static final Query.Builder<SongPlayCount> BUILDER_SONG_PLAY_COUNT =
            new Query.Builder<SongPlayCount>() {
                @Override
                public SongPlayCount build(Cursor cursor, String[] projection) {
                    String absolutePath = cursor.getString(
                            cursor.getColumnIndex(PROJECTION_SONG_PLAY_COUNT[0]));
                    int playCount = cursor.getInt(
                            cursor.getColumnIndex(PROJECTION_SONG_PLAY_COUNT[1]));
                    long lastPlayCount = cursor.getLong(
                            cursor.getColumnIndex(PROJECTION_SONG_PLAY_COUNT[2]));
                    return new SongPlayCount(absolutePath, playCount, lastPlayCount);
                }
            };

    private static final Function<Object[], List<Song>> COMBINER =
            new Function<Object[], List<Song>>() {
        @Override
        public List<Song> apply(Object[] objects) throws Exception {
            List<Song> result = new ArrayList<>();
            for (Object obj : objects) {
                @SuppressWarnings("unchecked")
                List<Song> items = (List<Song>) obj;
                result.addAll(items);
            }
            return result;
        }
    };

    /*package*/ static Flowable<List<Song>> queryAll(
            final ContentResolver resolver,
            final String sortOrder) {
        String selection = null;
        String[] selectionArgs = null;
        return Query.query(
                resolver,
                URI,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG);
    }

    /*package*/ static Flowable<List<Song>> queryAll(
            final ContentResolver resolver) {
        return queryAll(resolver, Sort.BY_TITLE);
    }

    /*package*/ static Flowable<List<Song>> queryAllFiltered(
            final ContentResolver resolver,
            final String filter) {
        final String selection = MediaStore.Audio.Media.TITLE + " LIKE ?";
        final String[] selectionArgs = new String[]{ "%" + filter + "%" };
        final String sortOrder = Sort.BY_TITLE;
        return Query.query(
                resolver,
                URI,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG);
    }

    /*package*/ static Flowable<Song> querySingle(
            final ContentResolver resolver,
            final long itemId) {
        return Query.querySingle(
                resolver,
                URI,
                PROJECTION_SONG,
                itemId,
                BUILDER_SONG);
    }

    /*package*/ static Flowable<Song> querySingleByPath(
            final ContentResolver resolver,
            final String path) {
        String selection = MediaStore.Audio.Media.DATA + "=?";
        String[] selectionArgs = new String[] { path };
        String sortOrder = null;

        return Query.query(
                resolver,
                URI,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG)
                .map(new Function<List<Song>, Song>() {
                    @Override
                    public Song apply(List<Song> songs) throws Exception {
                        if (songs != null && songs.size() > 0) {
                            return songs.get(0);
                        } else {
                            return null;
                        }
                    }
                });
    }

    /*package*/ static Flowable<List<Song>> queryAllFavourites(
            final ContentResolver resolver
    ) {
        final Uri favUri = AppMediaStore.Favourites.getContentUri();
        final Uri songsUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        final List<Uri> uris = Arrays.asList(favUri, songsUri);
        return Query.createFlowable(
                resolver,
                uris,
                new Callable<List<Song>>() {
                    @Override
                    public List<Song> call() throws Exception {
                        String[] projection = { AppMediaStore.Favourites.PATH };
                        String selectionNull = null;
                        String[] selectionArgsNull = null;
                        String sortOrderNull = null;
                        Cursor cursor = resolver.query(
                                favUri,
                                projection,
                                selectionNull,
                                selectionArgsNull,
                                sortOrderNull);

                        if (cursor == null) {
                            throw Query.genNullCursorErr(favUri);
                        }

                        List<Song> items = new ArrayList<>(cursor.getCount());
                        try {
                            if (cursor.moveToFirst()) {
                                do {
                                    String path = cursor.getString(
                                            cursor.getColumnIndex(projection[0]));

                                    try {
                                        Song item = querySingleByPath(
                                                resolver,
                                                path)
                                                .firstOrError()
                                                .blockingGet();

                                        items.add(item);
                                    } catch (Throwable ignored) {
                                    }
                                } while (cursor.moveToNext());
                            }
                        } finally {
                            cursor.close();
                        }

                        return items;
                    }
                });
    }

    /*package*/ static Flowable<List<Song>> queryForAlbum(
            final ContentResolver resolver,
            final Album album,
            final String sortOrder) {
        String selection = "is_music != 0 and album_id = " + album.getId();
        String[] selectionArgs = null;
        return Query.query(
                resolver,
                URI,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG);
    }

    /*package*/ static Flowable<List<Song>> queryForAlbums(
            final ContentResolver resolver,
            final Collection<Album> albums) {
        List<Flowable<List<Song>>> sources = new ArrayList<>(albums.size());
        for (Album album : albums) {
            sources.add(queryForAlbum(resolver, album, Sort.BY_TITLE));
        }
        return Flowable.combineLatest(sources, COMBINER);
    }

    /*package*/ static Flowable<List<Song>> queryForArtist(
            final ContentResolver resolver,
            final Artist artist,
            final String sortOrder) {
        String selection = "is_music != 0  and artist_id = " + artist.getId();
        String[] selectionArgs = null;
        return Query.query(
                resolver,
                URI,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG);
    }

    /*package*/ static Flowable<List<Song>> queryForArtists(
            final ContentResolver resolver,
            final Collection<Artist> artists) {
        List<Flowable<List<Song>>> sources = new ArrayList<>(artists.size());
        for (Artist artist : artists) {
            sources.add(queryForArtist(resolver, artist, Sort.BY_TITLE));
        }
        return Flowable.combineLatest(sources, COMBINER);
    }

    /*package*/ static Flowable<List<Song>> queryForGenre(
            final ContentResolver resolver,
            final Genre genre,
            final String sortOrder) {
        Uri uri = MediaStore.Audio.Genres.Members
                .getContentUri("external", genre.getId());
        String selection = null;
        String[] selectionArgs = null;
        return Query.query(
                resolver,
                uri,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG);
    }

    /*package*/ static Flowable<List<Song>> queryForGenres(
            final ContentResolver resolver,
            final Collection<Genre> genres) {
        List<Flowable<List<Song>>> sources = new ArrayList<>(genres.size());
        for (Genre genre : genres) {
            sources.add(queryForGenre(resolver, genre, Sort.BY_TITLE));
        }
        return Flowable.combineLatest(sources, COMBINER);
    }

    /*package*/ static Flowable<List<Song>> queryForPlaylist(
            final ContentResolver resolver,
            final Playlist playlist,
            final String sortOrder) {
        Uri uri = MediaStore.Audio.Playlists.Members
                .getContentUri("external", playlist.getId());
        String selection = null;
        String[] selectionArgs = null;
        return Query.query(
                resolver,
                uri,
                PROJECTION_PLAYLIST_MEMBER,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_PLAYLIST_MEMBER);
    }

    /*package*/ static Flowable<List<Song>> queryForPlaylists(
            final ContentResolver resolver,
            final Collection<Playlist> playlists) {
        List<Flowable<List<Song>>> sources = new ArrayList<>(playlists.size());
        for (Playlist playlist : playlists) {
            sources.add(queryForPlaylist(resolver, playlist, Sort.BY_TITLE));
        }
        return Flowable.combineLatest(sources, COMBINER);
    }

    /*package*/ static Flowable<List<Song>> queryForMyFile(
            final ContentResolver resolver,
            final MyFile myFile,
            final String sortOrder) {
        File javaFile = myFile.getJavaFile();
        String path = javaFile.getAbsolutePath();
        final String selection;
        final String[] selectionArgs;
        if (javaFile.isFile()) {
            // maybe it's an audio file
            selection = MediaStore.Audio.Media.DATA + " like ?";
            selectionArgs = new String[] {"%" + path + "%"};
        } else {
            // it's a folder, let's search it for audio files
            selection = MediaStore.Audio.Media.DATA + " like ?";
            selectionArgs = new String[] {"%" + path + "/%"};
        }
        return Query.query(
                resolver,
                URI,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG
        );
    }

    /*package*/ static Flowable<List<Song>> queryForMyFiles(
            final ContentResolver resolver,
            final Collection<MyFile> myFiles) {
        List<Flowable<List<Song>>> sources = new ArrayList<>(myFiles.size());
        for (MyFile myFile : myFiles) {
            sources.add(queryForMyFile(resolver, myFile, Sort.BY_TITLE));
        }
        return Flowable.combineLatest(sources, COMBINER);
    }

    /*package*/ static Flowable<List<Song>> queryRecentlyAdded(
            final ContentResolver resolver,
            final long dateAdded
    ) {
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0" + " AND "
                + MediaStore.Audio.Media.DATE_ADDED + ">" + dateAdded;
        String[] selectionArgs = null;
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
        return Query.query(
                resolver,
                URI,
                PROJECTION_SONG,
                selection,
                selectionArgs,
                sortOrder,
                BUILDER_SONG);
    }

    /*package*/ static Flowable<Boolean> isFavourite(
            final ContentResolver resolver,
            final Song item
    ) {
        return Query.createFlowable(
                resolver,
                AppMediaStore.Favourites.getContentUri(),
                new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        Uri uri = ContentUris.withAppendedId(
                                AppMediaStore.Favourites.getContentUri(), item.getId());
                        Cursor cursor = resolver
                                .query(uri, null, null, null, null);
                        if (cursor != null) {
                            boolean isFavourite = false;
                            try {
                                isFavourite = cursor.moveToFirst();
                            } finally {
                                cursor.close();
                            }
                            return isFavourite;
                        } else {
                            return false;
                        }
                    }
                }
        );
    }

    /*package*/ static Completable changeFavourite(
            final ContentResolver resolver,
            final Song item
    ) {
        return Completable.fromCallable(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Uri uri = ContentUris.withAppendedId(
                        AppMediaStore.Favourites.getContentUri(), item.getId());
                Cursor cursor = resolver.query(uri, null, null, null, null);

                if (cursor != null) {
                    boolean isFavourite = false;
                    try {
                        isFavourite = cursor.moveToFirst();
                    } finally {
                        cursor.close();
                    }

                    if (isFavourite) {
                        String selection = AppMediaStore.Favourites._ID + " = " + item.getId();
                        String[] selectionArgs = null;
                        int deletedCount = resolver.delete(
                                AppMediaStore.Favourites.getContentUri(),
                                selection,
                                selectionArgs);

                        if (deletedCount != 1) {
                            // TODO: throw an exception if it failed to delete
                        }
                        return false;
                    } else {
                        ContentValues values = new ContentValues();
                        values.put(AppMediaStore.Favourites._ID, item.getId());
                        values.put(AppMediaStore.Favourites.PATH, item.getSource());
                        values.put(AppMediaStore.Favourites.TIME_ADDED, System.currentTimeMillis());

                        Uri insertedCount = resolver.insert
                                (AppMediaStore.Favourites.getContentUri(),
                                        values);

                        if (insertedCount == null) {
                            // TODO: throw an exception if it failed to insert
                        }
                        return true;
                    }
                } else {
                    return false;
                }
            }
        });
    }

    /*package*/ static Completable update(
            final ContentResolver resolver,
            final Song item,
            final String title,
            final String album,
            final String artist,
            final String genre
    ) {
        return Completable.fromAction(new Action() {
            @Override
            public void run() throws Exception {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Audio.Media.TITLE, title);
                cv.put(MediaStore.Audio.Media.ALBUM, album);
                cv.put(MediaStore.Audio.Media.ARTIST, artist);

                final Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.getId());

                int updatedCount = resolver.update(uri, cv, null, null);

                resolver.notifyChange(uri, null);

                if (updatedCount == 0) {
                    // TODO: throw an exception if it failed to update
                }
            }
        });
    }

    //region SongWithPlayCount queries
    /**
     * Returns a flowable that emits {@link SongWithPlayCount} item for the given <code>song</code>.
     *
     * @param resolver content resolver.
     * @param song to query the play count.
     * @return a flowable that emits {@link SongWithPlayCount}.
     */
    /*package*/ static Flowable<SongWithPlayCount> getSongWithPlayCount(
            final ContentResolver resolver,
            final Song song
    ) {
        final String targetPath = song.getSource();
        return Flowable.create(new FlowableOnSubscribe<String>() {
            @Override
            public void subscribe(final FlowableEmitter<String> emitter) {
                if (!emitter.isCancelled()) {
                    final SongPlayCounter.Watcher w = new SongPlayCounter.Watcher() {
                        @Override
                        public void onChanged(String absolutePath) {
                            if (targetPath.equals(absolutePath)) {
                                emitter.onNext(absolutePath);
                            }
                        }
                    };

                    SongPlayCounter.startWatching(w);

                    emitter.setDisposable(Disposables.fromAction(new Action() {
                        @Override
                        public void run() {
                            SongPlayCounter.stopWatching(w);
                        }
                    }));
                }

                if (!emitter.isCancelled()) {
                    emitter.onNext(targetPath);
                }
            }
        }, BackpressureStrategy.LATEST)
                .map(new Function<String, SongWithPlayCount>() {
                    @Override
                    public SongWithPlayCount apply(String s) throws Exception {
                        final Uri uri = AppMediaStore.SongPlayCount.getContentUri();

                        final String[] projection =
                                new String[] {
                                        AppMediaStore.SongPlayCount.PLAY_COUNT,
                                        AppMediaStore.SongPlayCount.LAST_PLAY_TIME
                                };

                        final String selection = AppMediaStore.SongPlayCount.ABSOLUTE_PATH + "=?";
                        final String[] selectionArgs = new String[] { song.getSource() };

                        Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);

                        if (cursor == null) {
                            throw Query.genNullCursorErr(uri);
                        }

                        final int playCount;
                        final Long lastPlayTime;
                        try {
                            if (cursor.moveToFirst()) {
                                playCount = cursor.getInt(cursor.getColumnIndex(projection[0]));
                                lastPlayTime = cursor.getLong(cursor.getColumnIndex(projection[1]));
                            } else {
                                playCount = 0;
                                lastPlayTime = null;
                            }
                        } finally {
                            cursor.close();
                        }

                        return new SongWithPlayCount(song, playCount, lastPlayTime);
                    }
                });
    }

    /*package*/ static Flowable<List<SongWithPlayCount>> querySongsWithPlayCount(
        final ContentResolver resolver,
        final int minPlayCount
    ) {
        final Uri songPlayCountUri = AppMediaStore.SongPlayCount.getContentUri();
        if (minPlayCount > 0) {
            final String selection = AppMediaStore.SongPlayCount.PLAY_COUNT + ">= ?";
            final String[] selectionArgs = new String[] { String.valueOf(minPlayCount) };
            return Query.query(
                resolver,
                songPlayCountUri,
                PROJECTION_SONG_PLAY_COUNT,
                selection,
                selectionArgs,
                null,
                BUILDER_SONG_PLAY_COUNT
            ).switchMap(new Function<List<SongPlayCount>, Publisher<List<SongWithPlayCount>>>() {
                @Override
                public Publisher<List<SongWithPlayCount>> apply(List<SongPlayCount> counts) {
                    final List<Flowable<SongWithPlayCount>> sources = new ArrayList<>(counts.size());
                    for (final SongPlayCount count : counts) {
                        Flowable<SongWithPlayCount> source = querySingleByPath(resolver, count.absolutePath)
                            .map(new Function<Song, SongWithPlayCount>() {
                                @Override
                                public SongWithPlayCount apply(Song song) {
                                    return new SongWithPlayCount(song, count.playCount, count.lastPlayTime);
                                }
                            });
                        sources.add(source);
                    }
                    return Flowable.combineLatest(
                        sources,
                        new Function<Object[], List<SongWithPlayCount>>() {
                            @Override
                            public List<SongWithPlayCount> apply(Object[] objects) {
                                List<SongWithPlayCount> result = new ArrayList<>(objects.length);
                                for (Object obj : objects) {
                                    @SuppressWarnings("unchecked")
                                    SongWithPlayCount item = (SongWithPlayCount) obj;
                                    result.add(item);
                                }
                                return result;
                            }
                        }
                    );
                }
            });
        } else {
            return queryAll(resolver, Sort.BY_TITLE)
                .switchMap(new Function<List<Song>, Publisher<List<SongWithPlayCount>>>() {
                    @Override
                    public Publisher<List<SongWithPlayCount>> apply(List<Song> songs) {
                        List<Flowable<SongWithPlayCount>> sources = new ArrayList<>(songs.size());
                        for (final Song song : songs) {
                            Flowable<SongWithPlayCount> source =
                                getSongWithPlayCount(resolver, song);

                            sources.add(source);
                        }

                        return Flowable.combineLatest(sources, new Function<Object[], List<SongWithPlayCount>>() {
                            @Override
                            public List<SongWithPlayCount> apply(Object[] objects) {
                                List<SongWithPlayCount> items = new ArrayList<>(objects.length);
                                for (Object obj : objects) {
                                    items.add((SongWithPlayCount) obj);
                                }
                                return items;
                            }
                        });
                    }
                });
        }
    }

    /*package*/ static Completable addSongPlayCount(
            final ContentResolver resolver,
            final Song song,
            final int delta
    ) {
        return Completable.fromAction(new Action() {
            @Override
            public void run() throws Exception {
                final Uri uri = AppMediaStore.SongPlayCount.getContentUri();
                final String[] projection = new String[] { AppMediaStore.SongPlayCount.PLAY_COUNT };
                final String selection = AppMediaStore.SongPlayCount.ABSOLUTE_PATH + "=?";
                final String[] selectionArgs = new String[] { song.getSource() };
                Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);

                if (cursor == null) {
                    throw Query.genNullCursorErr(uri);
                }

                final int currentPlayCount;
                final boolean entityExists;
                try {
                    if (cursor.moveToFirst()) {
                        entityExists = true;
                        currentPlayCount = cursor.getInt(cursor.getColumnIndex(projection[0]));
                    } else {
                        entityExists = false;
                        currentPlayCount = 0;
                    }
                } finally {
                    cursor.close();
                }

                final int updatedPlayCount = currentPlayCount + delta;
                final long lastPlayTime = System.currentTimeMillis();

                if (entityExists) {
                    ContentValues values = new ContentValues(2);
                    values.put(AppMediaStore.SongPlayCount.PLAY_COUNT, updatedPlayCount);
                    values.put(AppMediaStore.SongPlayCount.LAST_PLAY_TIME, lastPlayTime);
                    int updatedCount = resolver.update(uri, values, selection, selectionArgs);
                    if (updatedCount == 0) {
                        // TODO: throw an exception
                    }
                } else {
                    ContentValues values = new ContentValues(3);
                    values.put(AppMediaStore.SongPlayCount.ABSOLUTE_PATH, song.getSource());
                    values.put(AppMediaStore.SongPlayCount.PLAY_COUNT, updatedPlayCount);
                    values.put(AppMediaStore.SongPlayCount.LAST_PLAY_TIME, lastPlayTime);
                    Uri resultUri = resolver.insert(uri, values);
                    if (resultUri == null) {
                        // TODO: throw an exception
                    }
                }

                SongPlayCounter.dispatchChanged(song.getSource());

            }
        });
    }
    //endregion

    private SongQuery() {
    }

}
