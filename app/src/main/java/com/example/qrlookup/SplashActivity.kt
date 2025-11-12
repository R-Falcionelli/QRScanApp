package com.example.qrlookup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.logoImageView)

        // Fade in
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        logo.startAnimation(fadeIn)

        // Quand le fade in est terminé, fade out et lancer MainActivity
        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                val fadeOut = AnimationUtils.loadAnimation(this@SplashActivity, R.anim.fade_out)
                logo.startAnimation(fadeOut)

                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        // Lancer MainActivity
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        finish()
                    }

                    override fun onAnimationRepeat(animation: Animation?) {}
                })
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }
}