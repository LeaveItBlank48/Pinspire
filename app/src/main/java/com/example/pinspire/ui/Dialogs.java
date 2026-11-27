package com.example.pinspire.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.pinspire.data.Board;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class Dialogs {
    /** Returns an error to keep the dialog open, or null to close it. */
    public interface BoardResult { @Nullable String apply(String name, String description); }

    private Dialogs() {}

    public static void boardEditor(Context c, String title, @Nullable Board existing, String action, BoardResult result) {
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 22);
        form.setPadding(pad, Ui.dp(c, 8), pad, 0);

        TextInputLayout nameLayout = new TextInputLayout(c, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        nameLayout.setHint("Board name");
        nameLayout.setCounterEnabled(true);
        nameLayout.setCounterMaxLength(50);
        TextInputEditText name = new TextInputEditText(nameLayout.getContext());
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameLayout.addView(name);
        form.addView(nameLayout);

        TextInputLayout descLayout = new TextInputLayout(c, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        descLayout.setHint("Description (optional)");
        TextInputEditText desc = new TextInputEditText(descLayout.getContext());
        desc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        desc.setMaxLines(3);
        descLayout.addView(desc);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(c, 6);
        form.addView(descLayout, lp);

        if (existing != null) {
            name.setText(existing.name);
            desc.setText(existing.description);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(action, null)
                .create();
        dialog.setOnShowListener(d -> {
            name.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String error = result.apply(String.valueOf(name.getText()), String.valueOf(desc.getText()));
                if (error == null) dialog.dismiss();
                else nameLayout.setError(error);
            });
        });
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    public static void confirm(Context c, String title, String message, String action, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(action, (d, w) -> onConfirm.run())
                .show();
    }
}
