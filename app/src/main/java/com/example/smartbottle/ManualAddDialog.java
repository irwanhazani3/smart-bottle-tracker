package com.example.smartbottle;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class ManualAddDialog {

    public interface OnDrinkAddedListener {
        void onDrinkAdded(int amountMl);
    }

    public static void show(Context context, OnDrinkAddedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Tambah Air Manual");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_manual_add, null);
        EditText etAmount = view.findViewById(R.id.etAmount);
        builder.setView(view);

        builder.setPositiveButton("Tambah", (dialog, which) -> {
            String input = etAmount.getText().toString();
            if (!input.isEmpty()) {
                int amount = Integer.parseInt(input);
                if (amount > 0) {
                    listener.onDrinkAdded(amount);
                } else {
                    Toast.makeText(context, "Masukkan jumlah yang valid", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Batal", null);
        builder.show();
    }
}