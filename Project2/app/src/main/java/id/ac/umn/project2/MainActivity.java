package id.ac.umn.project2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Button btn_profile, btn_login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        buttonSetter();
    }
    private void init(){
        btn_profile = findViewById(R.id.btn_profil);
        btn_login = findViewById(R.id.btn_login);
    }
    private void buttonSetter(){
        btn_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent loginIntent = new Intent(id.ac.umn.project2.MainActivity.this, LoginActivity.class);
                startActivity(loginIntent);
            }
        });
        btn_profile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent profileIntent = new Intent(id.ac.umn.project2.MainActivity.this, id.ac.umn.project2.ProfileActivity.class);
                startActivity(profileIntent);
            }
        });
    }
}