package com.example.pinspire.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.AppExecutors;
import com.example.pinspire.data.Board;
import com.example.pinspire.data.ImageStore;
import com.example.pinspire.data.Palettes;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.widget.MoodCanvasView;

import java.util.ArrayList;
import java.util.List;

/** Turn a board into a free-form moodboard collage, then save or share it as an image. */
public class StudioFragment extends Fragment {
    private static final String ARG_BOARD = "board";
    private static final int MAX_ITEMS = 12;

    private MoodCanvasView canvas;
    private Board board;
    private PinRepository repo;
    private int[] backgrounds;
    private int bgIndex;

    public static StudioFragment newInstance(String board) {
        StudioFragment f = new StudioFragment();
        Bundle b = new Bundle();
        b.putString(ARG_BOARD, board);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_studio, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity main = (MainActivity) requireActivity();
        repo = PinRepository.get(requireContext());
        board = repo.board(requireArguments().getString(ARG_BOARD));
        view.findViewById(R.id.back).setOnClickListener(v -> main.goBack());
        if (board == null) { view.post(main::goBack); return; }

        canvas = view.findViewById(R.id.canvas);
        View progress = view.findViewById(R.id.progress);
        ((TextView) view.findViewById(R.id.board_name)).setText(board.name);

        List<Pin> pins = new ArrayList<>();
        for (Pin p : repo.pinsIn(board)) if (p.hasImage() && pins.size() < MAX_ITEMS) pins.add(p);
        int[] mood = Palettes.blend(repo.pinsIn(board), 6);
        canvas.setStrip(mood);
        canvas.setCaption(board.name);
        backgrounds = new int[3 + mood.length];
        backgrounds[0] = Color.WHITE;
        backgrounds[1] = 0xfff3efe7;
        backgrounds[2] = 0xff151515;
        System.arraycopy(mood, 0, backgrounds, 3, mood.length);

        canvas.setListener(() -> repo.saveStudio(board, canvas.toJson().toString()));
        view.findViewById(R.id.shuffle).setOnClickListener(v -> canvas.shuffle());
        view.findViewById(R.id.background).setOnClickListener(v -> {
            bgIndex = (bgIndex + 1) % backgrounds.length;
            canvas.setBoardBackground(backgrounds[bgIndex]);
            repo.saveStudio(board, canvas.toJson().toString());
        });
        view.findViewById(R.id.strip).setOnClickListener(v -> {
            canvas.setShowStrip(!canvas.showStrip());
            repo.saveStudio(board, canvas.toJson().toString());
        });
        view.findViewById(R.id.caption).setOnClickListener(v -> {
            canvas.setShowCaption(!canvas.showCaption());
            repo.saveStudio(board, canvas.toJson().toString());
        });
        view.findViewById(R.id.export).setOnClickListener(v -> export(main));
        view.findViewById(R.id.share).setOnClickListener(v -> share());

        // Load downsampled copies off the main thread.
        final Context app = requireContext().getApplicationContext();
        final String saved = board.studio;
        AppExecutors.io(() -> {
            ArrayList<MoodCanvasView.Item> items = new ArrayList<>();
            for (Pin p : pins) {
                FutureTarget<Bitmap> target = Glide.with(app).asBitmap().load(com.example.pinspire.data.ImageModels.of(p.cover())).submit(560, 560);
                try {
                    Bitmap b = target.get();
                    // Copy so the canvas owns its pixels; Glide may recycle its own bitmap after clear().
                    Bitmap own = b.copy(Bitmap.Config.ARGB_8888, false);
                    if (own != null) items.add(new MoodCanvasView.Item(p.id, own));
                } catch (Exception ignored) {
                } finally {
                    Glide.with(app).clear(target);
                }
            }
            AppExecutors.main(() -> {
                if (getView() == null) return;
                progress.setVisibility(View.GONE);
                if (items.isEmpty()) {
                    MainActivity.notify("Add pins with photos to this board to build a moodboard", null, null);
                    return;
                }
                canvas.setItems(items);
                if (!canvas.applyJson(saved)) canvas.shuffle();
                for (int i = 0; i < backgrounds.length; i++) if (backgrounds[i] == canvas.boardBackground()) bgIndex = i;
            });
        });
    }

    private void export(MainActivity main) {
        if (canvas == null || canvas.itemCount() == 0 || !PinActions.ensureStoragePermission(main)) return;
        final Bitmap bmp = canvas.render(1440);
        final Context app = requireContext().getApplicationContext();
        final String name = "Pinspire-moodboard-" + System.currentTimeMillis();
        AppExecutors.io(() -> {
            String error = null;
            try { ImageStore.exportBitmapToGallery(app, bmp, name); }
            catch (Exception e) { error = e.getMessage(); }
            finally { bmp.recycle(); }
            final String err = error;
            AppExecutors.main(() -> MainActivity.notify(err == null ? "Moodboard saved to Pictures/Pinspire" : "Couldn't save: " + err, null, null));
        });
    }

    private void share() {
        if (canvas == null || canvas.itemCount() == 0) return;
        final Bitmap bmp = canvas.render(1440);
        final Context app = requireContext().getApplicationContext();
        final String title = board.name;
        AppExecutors.io(() -> {
            Uri uri = null;
            try { uri = ImageStore.shareBitmap(app, bmp, "moodboard"); } catch (Exception ignored) {}
            finally { bmp.recycle(); }
            final Uri shared = uri;
            AppExecutors.main(() -> {
                MainActivity a = MainActivity.current();
                if (a == null) return;
                if (shared == null) { MainActivity.notify("Couldn't prepare the image", null, null); return; }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/jpeg");
                send.putExtra(Intent.EXTRA_STREAM, shared);
                send.putExtra(Intent.EXTRA_TEXT, title + " moodboard, made with Pinspire");
                send.setClipData(ClipData.newRawUri("", shared));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { a.startActivity(Intent.createChooser(send, "Share moodboard")); }
                catch (Exception e) { MainActivity.notify("No app available to share with", null, null); }
            });
        });
    }
}
