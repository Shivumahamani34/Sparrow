package com.smlab.sparrow.ui.speak;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.smlab.sparrow.R;

public class RoomActivity extends AppCompatActivity {
    Button createRoom, joinRoom;
    EditText roomIdTextMain;

    SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.room_title_settings);
        }


        joinRoom = findViewById(R.id.join_room);
        createRoom = findViewById(R.id.create_room);
        roomIdTextMain = findViewById(R.id.room_id);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);


//        createRoom.setOnClickListener(v -> {
//            if (ZeenyDevice.Companion.isConnected()) {
//                showCustomDialog();
//                LogHelper.d("Zeeny Device Connected!!");
//            } else {
//                showCustomDialog();
//                Toast.makeText(this, getString(R.string.device_no_connection), Toast.LENGTH_SHORT).show();
//            }
//        });

        createRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RoomActivity.this.showCustomDialog();
            }
        });


        joinRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String roomName = roomIdTextMain.getText().toString();
                TalkiePreferenceHelper.INSTANCE.setRoomId(roomName);
                if (!TextUtils.isEmpty(roomName)) {
                    RoomActivity.this.startActivity(new Intent(RoomActivity.this.getApplicationContext(), RoomViewActivity.class));
                } else if (TextUtils.isEmpty(roomName)) {
                    roomIdTextMain.setError(RoomActivity.this.getResources().getString(R.string.error_message));
                }
            }
        });

    }

    private void showCustomDialog() {
        //before inflating the custom alert dialog layout, we will get the current activity viewgroup
        ViewGroup viewGroup = findViewById(android.R.id.content);
        //then we will inflate the custom alert dialog xml that we created
        View dialogView = LayoutInflater.from(this).inflate(R.layout.custom_dialog, viewGroup, false);
        //Now we need an AlertDialog.Builder object
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //setting the view of the builder to our custom view that we already inflated
        builder.setView(dialogView);
        //finally creating the alert dialog and displaying it
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
        final EditText roomIdText = dialogView.findViewById(R.id.text_room_id);
        roomIdText.setText(roomIdText.getText().toString());
        dialogView.findViewById(R.id.buttonOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TalkiePreferenceHelper.INSTANCE.setRoomId(roomIdText.getText().toString());
                roomIdTextMain.setText(roomIdText.getText().toString());
                RoomActivity.this.startActivity(new Intent(RoomActivity.this.getApplicationContext(), RoomViewActivity.class));
                alertDialog.hide();
            }
        });
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

}
