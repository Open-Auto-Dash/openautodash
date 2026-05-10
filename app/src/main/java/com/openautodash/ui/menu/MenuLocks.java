package com.openautodash.ui.menu;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.openautodash.R;
import com.openautodash.pairing.DashPairingManager;
import com.openautodash.repositorys.VehicleRepository;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MenuLocks#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MenuLocks extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private DashPairingManager pairingManager;
    private LinearLayout pairedPhonesLayout;

    public MenuLocks() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MenuLocks.
     */
    // TODO: Rename and change types and number of parameters
    public static MenuLocks newInstance(String param1, String param2) {
        MenuLocks fragment = new MenuLocks();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pairingManager = new DashPairingManager(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_locks, container, false);
        Button pairButton = view.findViewById(R.id.btn_pair_new_phone);
        pairedPhonesLayout = view.findViewById(R.id.layout_paired_phones);
        pairButton.setOnClickListener(v -> showPairingQr());
        renderPairedPhones();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        renderPairedPhones();
    }

    private void showPairingQr() {
        try {
            String payload = pairingManager.createPairingQrPayload();
            Bitmap qr = createQrBitmap(payload, 700);
            ImageView image = new ImageView(requireContext());
            image.setImageBitmap(qr);
            image.setAdjustViewBounds(true);
            image.setPadding(24, 24, 24, 24);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Scan with Phone")
                    .setView(image)
                    .setPositiveButton("Done", (d, which) -> renderPairedPhones())
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Pairing Error")
                    .setMessage("Could not create pairing QR.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void renderPairedPhones() {
        if (pairedPhonesLayout == null) return;
        pairedPhonesLayout.removeAllViews();
        List<DashPairingManager.PairedPhone> phones = pairingManager.listPairedPhoneRecords();
        if (phones.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No paired phones yet");
            pairedPhonesLayout.addView(empty);
            return;
        }
        for (DashPairingManager.PairedPhone phone : phones) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);
            row.setGravity(Gravity.START);

            TextView phoneText = new TextView(requireContext());
            String pairedAt = phone.pairedAt > 0
                    ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(phone.pairedAt))
                    : "Unknown date";
            phoneText.setText(phone.displayName + "\n" + phone.phoneId + "\nPaired " + pairedAt);

            Button deleteButton = new Button(requireContext());
            deleteButton.setText("Delete");
            deleteButton.setOnClickListener(v -> {
                pairingManager.clearPhone(phone.phoneId);
                VehicleRepository.getInstance(requireContext().getApplicationContext()).updateBluetoothState(false);
                renderPairedPhones();
            });

            row.addView(phoneText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(deleteButton, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            pairedPhonesLayout.addView(row);
        }
    }

    private Bitmap createQrBitmap(String text, int size) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }
}
