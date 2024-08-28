package com.terasumi.sellerkeyboard.old

//import androidx.navigation.ui.AppBarConfiguration.Builder.build
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI.navigateUp
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private var appBarConfiguration: AppBarConfiguration? = null
    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding!!.root)

            setSupportActionBar(binding!!.toolbar)

            val navHostFragment =
                checkNotNull(supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)
            val navController = navHostFragment.navController
            appBarConfiguration = AppBarConfiguration.Builder(navController.graph).build()
            setupActionBarWithNavController(this, navController, appBarConfiguration!!)

            binding!!.fab.setOnClickListener { view: View? ->
                try {
                    // Change from fragment first to second
                    NavHostFragment.findNavController(navHostFragment)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error navigating to SecondFragment", e)
                }
            }
            navController.addOnDestinationChangedListener { controller: NavController?, destination: NavDestination, arguments: Bundle? ->
                try {
                    if (destination.id == R.id.FirstFragment) {
                        binding!!.fab.visibility = View.VISIBLE
                    } else {
                        binding!!.fab.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error changing FAB visibility", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return try {
            menuInflater.inflate(R.menu.menu_main, menu)
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Error inflating menu", e)
            false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return try {
            val id = item.itemId
            if (id == R.id.change_keyboard) {
                showKeyboardPickerDialog()
                true
            } else if (id == R.id.language_setting) {
                openOnScreenKeyboardSettings()
                true
            } else {
                super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling menu item selection", e)
            false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return try {
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?
            val navController = navHostFragment!!.navController
            navigateUp(navController, appBarConfiguration!!) || super.onSupportNavigateUp()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error navigating up", e)
            false
        }
    }

    fun showKeyboardPickerDialog() {
        try {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showInputMethodPicker()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing keyboard picker dialog", e)
        }
    }

    fun openOnScreenKeyboardSettings() {
        try {
            val keyboardSettingsIntent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(keyboardSettingsIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening on-screen keyboard settings", e)
        }
    }
}