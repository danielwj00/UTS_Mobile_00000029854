package id.ac.umn.project2;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ListLaguActivity extends AppCompatActivity implements
        RecycleSongTouchHelper.RecycleSongTouchHelperListener, id.ac.umn.project2.SongAdapter.SongAdapterListener,
        View.OnClickListener, MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener, SeekBar.OnSeekBarChangeListener {
    private static final Uri MEDIA_URI = Uri.parse("content://media");
    private static Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
    private final int STORAGE_PERMISSION_ID = 0;
    private List<Song> mSongList = new ArrayList<>();
    private RecyclerView mRecyclerViewSongs;
    private id.ac.umn.project2.SongAdapter mAdapter;
    private CoordinatorLayout mCoordinatorLayout;
    private LinearLayout mMediaLayout;
    private MediaPlayer mMediaPlayer;

    // TODO list
    private TextView mTvTitle;
    private ImageView mIvArtwork;
    private ImageView mIvPlay;
    private ImageView mIvPrevious;
    private ImageView mIvNext;
    private boolean isPlaying = false;
    private SeekBar songProgressBar;
    private TextView mTvCurrentDuration;
    private TextView mTvTotalDuration;
    private id.ac.umn.project2.TimeUtil timeUtil;
    private int currentSongIndex;
    // Handler to update UI timer, progress bar etc,.
    private Handler mHandler = new Handler();
    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            if (mMediaPlayer == null) return;
            long totalDuration = mMediaPlayer.getDuration();
            long currentDuration = mMediaPlayer.getCurrentPosition();
            mTvTotalDuration
                    .setText(String.format("%s", timeUtil.milliSecondsToTimer(totalDuration)));
            mTvCurrentDuration
                    .setText(String.format("%s", timeUtil.milliSecondsToTimer(currentDuration)));
            int progress = (timeUtil.getProgressPercentage(currentDuration, totalDuration));
            songProgressBar.setProgress(progress);
            mHandler.postDelayed(this, 100);
        }
    };

    private AlertDialog.Builder dialog;
    private LayoutInflater inflater;
    private View dialogView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_song);
        init();
        setUpAdapter();
        setUpListeners();
        getSongList();
        welcomeMessage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaPlayer.stop();
        mMediaPlayer.release();
        mMediaPlayer = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.optionmenu_list_song, menu);
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;

    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()==R.id.about){
            startActivity(new Intent(this, id.ac.umn.project2.ProfileLoggedActivity.class));
        } else if (item.getItemId() == R.id.logout) {
            startActivity(new Intent(this, id.ac.umn.project2.MainActivity.class));
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }else {
//            Log.d(this.getClass().getName(), "Option selected");
            super.onBackPressed();
        }
        return true;
    }

    private void init(){
        if (!checkStorePermission(STORAGE_PERMISSION_ID)) {
            showRequestPermission(STORAGE_PERMISSION_ID);
        }
        mMediaPlayer = new MediaPlayer();
        timeUtil = new id.ac.umn.project2.TimeUtil();
        mRecyclerViewSongs = findViewById(R.id.recycle_list_song);
        mCoordinatorLayout = findViewById(R.id.coordinator_layout);
        mMediaLayout = findViewById(R.id.layout_media);
        // TODO
        mIvArtwork = findViewById(R.id.iv_artwork);
        mIvPlay = findViewById(R.id.iv_play);
        mIvPrevious = findViewById(R.id.iv_previous);
        mIvNext = findViewById(R.id.iv_next);
        mTvTitle = findViewById(R.id.tv_title);
        mTvCurrentDuration = findViewById(R.id.songCurrentDurationLabel);
        mTvTotalDuration = findViewById(R.id.songTotalDurationLabel);
        songProgressBar = findViewById(R.id.songProgressBar);
    }

    private void setUpAdapter(){
        mAdapter = new id.ac.umn.project2.SongAdapter(getApplicationContext(), mSongList, this);
        RecyclerView.LayoutManager mLayoutManager =
                new LinearLayoutManager(getApplicationContext());
        mRecyclerViewSongs.setLayoutManager(mLayoutManager);
        mRecyclerViewSongs.setItemAnimator(new DefaultItemAnimator());
        mRecyclerViewSongs.setAdapter(mAdapter);
    }

    private void setUpListeners(){
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                new RecycleSongTouchHelper(0, ItemTouchHelper.LEFT, this);
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(mRecyclerViewSongs);
        mIvPlay.setOnClickListener(this);
        mIvPrevious.setOnClickListener(this);
        mIvNext.setOnClickListener(this);
        songProgressBar.setOnSeekBarChangeListener(this);
        mMediaPlayer.setOnCompletionListener(this);
    }

    private void getSongList(){
        //retrieve item_song info
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
        if (musicCursor != null && musicCursor.moveToFirst()) {
            //get columns
            int titleColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media._ID);
            int albumID = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ALBUM_ID);
            int artistColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ARTIST);
            int songLink = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.DATA);
            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                Uri thisSongLink = Uri.parse(musicCursor.getString(songLink));
                long some = musicCursor.getLong(albumID);
                Uri uri = ContentUris.withAppendedId(sArtworkUri, some);
                mSongList.add(new Song(thisId, thisTitle, thisArtist, uri.toString(),
                        thisSongLink.toString()));
            }
            while (musicCursor.moveToNext());
        }
        assert musicCursor != null;
        musicCursor.close();
        // Sort music alphabetically
        Collections.sort(mSongList, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
        mAdapter.notifyDataSetChanged();
    }

    private void welcomeMessage(){
        // TODO pop up menu
        dialog = new AlertDialog.Builder(id.ac.umn.project2.ListLaguActivity.this);
        inflater = getLayoutInflater();
        dialogView = inflater.inflate(R.layout.popup_list_song, null);
        dialog.setView(dialogView);
        dialog.setCancelable(true);
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
//        btnClosePopUp.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                dialog
//            }
//        });
        dialog.show();
    }

    private boolean checkStorePermission(int permission) {
        if (permission == STORAGE_PERMISSION_ID) {
            return id.ac.umn.project2.PermissionsUtil.checkPermissions(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            return true;
        }
    }

    private void showRequestPermission(int requestCode) {
        String[] permissions;
        if (requestCode == STORAGE_PERMISSION_ID) {
            permissions = new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        id.ac.umn.project2.PermissionsUtil.requestPermissions(this, requestCode, permissions);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            for (int i = 0, len = permissions.length; i < len; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    getSongList();
                    return;
                }
            }
        }
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
        if (viewHolder instanceof id.ac.umn.project2.SongAdapter.MyViewHolder) {
            // get the removed item name to display it in snack bar
            String name = mSongList.get(viewHolder.getAdapterPosition()).getTitle();
            // backup of removed item for undo purpose
            final Song deletedItem = mSongList.get(viewHolder.getAdapterPosition());
            final int deletedIndex = viewHolder.getAdapterPosition();
            // remove the item from recycler view
            mAdapter.removeItem(viewHolder.getAdapterPosition());
            //To delete song from device uncomment below code
//            File file = new File(deletedItem.getSongLink());
//            deleteMusic(file);
            // showing snack bar with Undo option
            Snackbar snackbar = Snackbar
                    .make(mCoordinatorLayout, name + " removed from library!", Snackbar.LENGTH_LONG);
            snackbar.setAction("UNDO", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // undo is selected, restore the deleted item
                    mAdapter.restoreItem(deletedItem, deletedIndex);
                }
            });
            snackbar.setActionTextColor(Color.YELLOW);
            snackbar.show();
        }
    }

    @Override
    public void onSongSelected(Song song) {
        playSong(song);
        currentSongIndex = mSongList.indexOf(song);

//        Intent nowPlayingIntent = new Intent(ListLaguActivity.this, NowPlayingActivity.class);
//        String title = song.getTitle();
//        String album = song.getThumbnail();
//        String path = song.getSongLink();
//        String artist = song.getArtist();
//        nowPlayingIntent.putExtra("title", title);
//        nowPlayingIntent.putExtra("path", path);
//        nowPlayingIntent.putExtra("artist", artist);
//        nowPlayingIntent.putExtra("album", album);
//        startActivity(nowPlayingIntent);
    }

    public boolean deleteMusic(final File file) {
        final String where = MediaStore.MediaColumns.DATA + "=?";
        final String[] selectionArgs = new String[]{
                file.getAbsolutePath()
        };
        final ContentResolver contentResolver = id.ac.umn.project2.ListLaguActivity.this.getContentResolver();
        final Uri filesUri = MediaStore.Files.getContentUri("external");
        contentResolver.delete(filesUri, where, selectionArgs);
        if (file.exists()) {
            contentResolver.delete(filesUri, where, selectionArgs);
        }
        return !file.exists();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_play:
                playMusic();
                break;
            case R.id.iv_previous:
                playPreviousSong();
                break;
            case R.id.iv_next:
                playNextSong();
                break;
            default:
                break;
        }
    }

    private void playMusic() {
        if (isPlaying) {
            mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_pause));
            isPlaying = false;
            mMediaPlayer.start();
            return;
        }
        mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_play));
        mMediaPlayer.pause();
        isPlaying = true;
    }

    public void playSong(Song song) {
        try {
            mMediaPlayer.reset();
//            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(song.getSongLink());
            Log.d(null, song.getSongLink());
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            // Displaying Song title
            isPlaying = true;
            mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_pause));
            mMediaLayout.setVisibility(View.VISIBLE);
            mTvTitle.setText(song.getTitle());
            Glide.with(this).load(song.getThumbnail()).placeholder(R.mipmap
                    .music_art_1).error(R.mipmap.music_art_1)
                    .crossFade().centerCrop().into(mIvArtwork);
            // set Progress bar values
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);
            // Updating progress bar
            updateProgressBar();
        } catch (IllegalArgumentException | IllegalStateException | IOException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void playNextSong() {
        if (currentSongIndex < (mSongList.size() - 1)) {
            Song song = mSongList.get(currentSongIndex + 1);
            playSong(song);
            currentSongIndex = currentSongIndex + 1;
        } else {
            playSong(mSongList.get(0));
            currentSongIndex = 0;
        }
    }
    private void playPreviousSong() {
        if (currentSongIndex > 0) {
            Song song = mSongList.get(currentSongIndex - 1);
            playSong(song);
            currentSongIndex = currentSongIndex - 1;
        } else {
            Song song = mSongList.get(mSongList.size() - 1);
            playSong(song);
            currentSongIndex = mSongList.size() - 1;
        }
    }

    public void updateProgressBar() {
        mHandler.postDelayed(mUpdateTimeTask, 100);
    }
    @Override
    public void onAudioFocusChange(int focusChange) {
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (currentSongIndex < (mSongList.size() - 1)) {
            playSong(mSongList.get(currentSongIndex + 1));
            currentSongIndex = currentSongIndex + 1;
        } else {
            playSong(mSongList.get(0));
            currentSongIndex = 0;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mHandler.removeCallbacks(mUpdateTimeTask);
        int totalDuration = mMediaPlayer.getDuration();
        int currentPosition = timeUtil.progressToTimer(seekBar.getProgress(), totalDuration);
        mMediaPlayer.seekTo(currentPosition);
        updateProgressBar();
    }
}
