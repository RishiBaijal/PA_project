package com.example.rishi.visualiser;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    public static String jimpleExtra = "JIMPLE";
    public static String shimpleExtra = "SHIMPLE";
    public static String grimpleExtra = "GRIMPLE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void sendCodeJimple(View view)
    {
        Intent intent = new Intent(this, JimpleCodeActivity.class);
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String codeJimple  = editText.getText().toString();
        intent.putExtra(jimpleExtra, codeJimple);
        startActivity(intent);
    }

    public void sendCodeShimple(View view)
    {
        Intent intent = new Intent(this, ShimpleCodeActivity.class);
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String codeJimple  = editText.getText().toString();
        intent.putExtra(shimpleExtra, codeJimple);
        startActivity(intent);
    }

    public void sendCodeGrimple(View view)
    {
        Intent intent = new Intent(this, GrimpleCodeActivity.class);
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String codeJimple  = editText.getText().toString();
        intent.putExtra(grimpleExtra, codeJimple);
        startActivity(intent);
    }
}
