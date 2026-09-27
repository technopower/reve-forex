package com.reve.forex
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
class MainActivity: ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme{ForexApp()}}}
}
