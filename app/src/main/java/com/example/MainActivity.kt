package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.SalesDiaryRepository
import com.example.ui.SalesDiaryApp
import com.example.ui.theme.MySalesDiaryTheme
import com.example.ui.viewmodel.SalesDiaryViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = SalesDiaryRepository(
            leadDao = database.leadDao(),
            courseDao = database.courseDao(),
        )
        val viewModel = ViewModelProvider(
            this,
            SalesDiaryViewModel.Factory(repository),
        )[SalesDiaryViewModel::class.java]

        setContent {
            MySalesDiaryTheme {
                SalesDiaryApp(viewModel = viewModel)
            }
        }
    }
}
