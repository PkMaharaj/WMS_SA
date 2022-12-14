package com.wms.superadmin.modules.bankbook.ui

import android.animation.Animator
import androidx.recyclerview.widget.LinearLayoutManager

import androidx.recyclerview.widget.RecyclerView

import android.animation.ValueAnimator

import android.animation.AnimatorListenerAdapter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context

import androidx.annotation.NonNull

import android.os.Build
import android.util.Property
import android.view.*
import android.view.ScaleGestureDetector.OnScaleGestureListener

import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener
import com.wms.superadmin.R
import java.lang.IllegalStateException


class PinchZoomItemTouchListener(context: Context?, listener: PinchZoomListener?) :
    OnItemTouchListener, OnScaleGestureListener {
    private val mScaleGestureDetector: ScaleGestureDetector
    private val mSpanSlop: Int
    private val mListener: PinchZoomListener?
    private var mEnabled = true
    private var mRecyclerView: RecyclerView? = null
    private var mOrientation = 0
    private var mIntercept = false
    private var mSpan = 0f
    private var mPosition = 0
    private var mDisallowInterceptTouchEvent = false
    fun setEnabled(enabled: Boolean) {
        mEnabled = enabled
    }

    override fun onInterceptTouchEvent(
        recyclerView: RecyclerView, event: MotionEvent
    ): Boolean {
        if (!mEnabled) {
            return false
        }

        // Bail out when onRequestDisallowInterceptTouchEvent is called and the motion event has started.
        if (mDisallowInterceptTouchEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> mDisallowInterceptTouchEvent = false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mDisallowInterceptTouchEvent = false
                    return false // Exit now as UP, CANCEL, MOVE and other events shouldn't be handled when disallowed.
                }
                else -> return false
            }
        }

        // Grab RV reference and current orientation.
        mRecyclerView = recyclerView
        mOrientation = if (recyclerView.layoutManager is LinearLayoutManager) {
            (recyclerView.layoutManager as LinearLayoutManager?)!!.orientation
        } else {
            throw IllegalStateException("PinchZoomItemTouchListener only supports LinearLayoutManager")
        }

        // Proxy to ScaleGestureDetector. Its onScaleBegin() method sets mIntercept when called.
        mScaleGestureDetector.onTouchEvent(event)
        return mIntercept
    }

    override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
        mScaleGestureDetector.onTouchEvent(event)
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        mDisallowInterceptTouchEvent = disallowIntercept
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        // Grab the current span and center position of the gesture.
        mSpan = getSpan(detector)
        val child: View? = mRecyclerView!!.findChildViewUnder(detector.focusX, detector.focusY)
        mPosition = if (child != null) mRecyclerView!!.getChildLayoutPosition(child) else RecyclerView.NO_POSITION

        // Determine if we should intercept, based on it being a valid position.
        mIntercept = mPosition != RecyclerView.NO_POSITION

        // Prevent ancestors from intercepting touch events if we will intercept.
        return if (mIntercept) {
            val recyclerViewParent = mRecyclerView!!.parent
            recyclerViewParent?.requestDisallowInterceptTouchEvent(true)
            true
        } else {
            false
        }
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        // Translate items around the center position.
        for (i in 0 until mRecyclerView!!.childCount) {
            val child: View = mRecyclerView!!.getChildAt(i)
            val position = mRecyclerView!!.getChildLayoutPosition(child)
            if (position != RecyclerView.NO_POSITION) {
                translateProperty.set(child, Math.max(0f, getSpan(detector) - mSpan) * if (position < mPosition) -2f else 2f)
            }
        }

        // Redraw item decorations.
        mRecyclerView!!.invalidateItemDecorations()
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        // Animate items returning to their resting translations.
        for (i in 0 until mRecyclerView!!.childCount) {
            val child: View = mRecyclerView!!.getChildAt(i)
            val animator: ObjectAnimator = ObjectAnimator.ofFloat(child,translateProperty,0F)
            animator.duration = SETTLE_DURATION_MS.toLong()
            animator.interpolator = SETTLE_INTERPOLATOR
            animator.start()

            // Prevent RV from recycling this child.
            child.setHasTransientState(true)
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    child.setHasTransientState(false)
                }
            })

            // Redraw item decorations on every frame, but only once per child.
            if (i == 0) {
                animator.addUpdateListener { mRecyclerView!!.invalidateItemDecorations() }
            }
        }

        // Invoke listener if the gesture and position are valid.
        val adapter = mRecyclerView!!.adapter
        if (mListener != null && getSpan(detector) - mSpan > mSpanSlop && adapter != null && mPosition < adapter.itemCount) {
            mListener.onPinchZoom(mPosition)
        }
        mIntercept = false
    }

    private fun getSpan(detector: ScaleGestureDetector): Float {
        return if (mOrientation == LinearLayoutManager.VERTICAL) {
            detector.currentSpanY
        } else {
            detector.currentSpanX
        }
    }

    private val translateProperty: Property<View, Float>
        private get() = if (mOrientation == LinearLayoutManager.VERTICAL) {
            View.TRANSLATION_Y
        } else {
            View.TRANSLATION_X
        }

    interface PinchZoomListener {
        fun onPinchZoom(position: Int)
    }

    companion object {
        private const val SETTLE_DURATION_MS = 250
        private val SETTLE_INTERPOLATOR: Interpolator = DecelerateInterpolator()
    }

    init {
        mScaleGestureDetector = ScaleGestureDetector(context, this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mScaleGestureDetector.isQuickScaleEnabled = false
        }
        mSpanSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
        mListener = listener
    }
}