package com.peramsuneel.cleanplayer
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.annotation.ColorRes
/** Monochrome Liquid Glass surfaces. Lightweight translucent layers avoid full-screen blur costs. */
internal object LiquidGlass {
 private fun dark(c:Context)=c.getSharedPreferences("clean_player_preferences",Context.MODE_PRIVATE).getBoolean("dark_theme",true)
 fun color(c:Context,@ColorRes id:Int):Int { val d=dark(c); return when(id) {
  R.color.player_background -> if(d) 0xFF080808.toInt() else 0xFFF3F3F3.toInt()
  R.color.player_surface -> if(d) 0xFF181818.toInt() else 0xFFE9E9E9.toInt()
  R.color.player_divider -> if(d) 0x55FFFFFF else 0x44000000
  R.color.player_secondary -> if(d) 0xFFBDBDBD.toInt() else 0xFF555555.toInt()
  R.color.player_accent -> if(d) Color.WHITE else 0xFF242424.toInt()
  R.color.player_primary -> if(d) Color.WHITE else 0xFF171717.toInt()
  R.color.player_buffered -> if(d) 0x99FFFFFF.toInt() else 0x66000000
  R.color.player_overlay -> if(d) 0x99000000.toInt() else 0x55000000
  R.color.player_ripple -> 0x33FFFFFF
  R.color.launcher_background -> Color.TRANSPARENT
  R.color.splash_background -> if(d) Color.BLACK else 0xFFF3F3F3.toInt()
  else -> androidx.core.content.ContextCompat.getColor(c,id)
 } }
 private fun dp(c:Context,v:Int)=v*c.resources.displayMetrics.density
 fun panel(c:Context,r:Int=20):Drawable { val d=dark(c); return GradientDrawable(GradientDrawable.Orientation.TL_BR,
  if(d) intArrayOf(0x38FFFFFF,0x24FFFFFF,0x12FFFFFF) else intArrayOf(0xF5FFFFFF.toInt(),0xE8FFFFFF.toInt(),0xD8FFFFFF.toInt())).apply {
  cornerRadius=dp(c,r); setStroke(dp(c,1).toInt(),if(d) 0x77FFFFFF else 0xFFFFFFFF.toInt())
 } }
 fun thumbnail(c:Context,r:Int=16):Drawable { val d=dark(c); return GradientDrawable(GradientDrawable.Orientation.TL_BR,
  if(d) intArrayOf(0x22FFFFFF,0x0FFFFFFF) else intArrayOf(0xCCFFFFFF.toInt(),0x99FFFFFF.toInt())).apply {
  cornerRadius=dp(c,r); setStroke(dp(c,1).toInt(),if(d) 0x66FFFFFF else 0xCCFFFFFF.toInt())
 } }
 fun controlPanel(c:Context,r:Int=24):Drawable=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
  intArrayOf(0xBB333333.toInt(),0x88111111.toInt(),0x66111111)).apply { cornerRadius=dp(c,r); setStroke(dp(c,1).toInt(),0xAAFFFFFF.toInt()) }
 fun button(c:Context,selected:Boolean=false):Drawable {
  val d=dark(c)
  val fill=when { d && selected -> intArrayOf(0x99555555.toInt(),0x77333333)
                  d -> intArrayOf(0x88333333.toInt(),0x55111111)
                  selected -> intArrayOf(0xF2FFFFFF.toInt(),0xDFFFFFFF.toInt())
                  else -> intArrayOf(0xDDFFFFFF.toInt(),0xBFFFFFFF.toInt()) }
  val b=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,fill).apply {
   cornerRadius=dp(c,16)
   setStroke(dp(c,1).toInt(),if(d) 0x88FFFFFF.toInt() else 0x77000000)
  }
  return RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),b,null)
 }
 /** Transparent player hit target: retains a subtle tap ripple without any visible border or frame. */
 fun playerIconButton(c:Context):Drawable =
  RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),ColorDrawable(Color.TRANSPARENT),null)
 fun controlButton(c:Context):Drawable =
  RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),controlPanel(c,18),null)
 fun circular(c:Context):Drawable=GradientDrawable(GradientDrawable.Orientation.TL_BR,
  intArrayOf(0xAA444444.toInt(),0x77111111)).apply {
  shape=GradientDrawable.OVAL; setStroke(dp(c,1).toInt(),Color.WHITE)
 }
 fun elevation(c:Context)=dp(c,5)
}
