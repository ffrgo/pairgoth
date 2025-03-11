package com.iqoid.pairgoth

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.iqoid.pairgoth.client.network.NetworkManager

class TourActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) //replace if you want

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkManager.pairGothApiService.getTours()
                if (response.isSuccessful) {
                    val tours = response.body()
                    // Use the tours data
                    Log.d("TourActivity", "Tours: $tours")
                } else {
                    // Handle the error
                    Log.e("TourActivity", "Error: ${response.errorBody()}")
                }
            } catch (e: Exception) {
                // Handle network exceptions
                Log.e("TourActivity", "Exception: ${e.message}")
            }
        }
    }
}
