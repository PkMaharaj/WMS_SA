package com.wms.superadmin.modules.stocksummary.sritem.ui

import StockReportAdapter
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.wms.superadmin.R
import com.wms.superadmin.appcomponents.base.BaseFragment
import com.wms.superadmin.appcomponents.di.MyApp
import com.wms.superadmin.appcomponents.utility.PreferenceHelper
import com.wms.superadmin.databinding.FragmentSrbranchBinding
import com.wms.superadmin.databinding.FragmentSrcategoryBinding
import com.wms.superadmin.databinding.FragmentSritemBinding
import com.wms.superadmin.databinding.FragmentSrsubcategoryBinding
import com.wms.superadmin.extensions.*
import com.wms.superadmin.modules.stocksummary.sritem.data.model.StockReportRowModel
import com.wms.superadmin.modules.stocksummary.sritem.data.viewmodel.SritemVM
import com.wms.superadmin.modules.stocksummary.stockreport.ui.StockreportActivity
import com.wms.superadmin.network.models.Login.LoginResponse
import com.wms.superadmin.network.models.stocksummary.StockReportRequest
import com.wms.superadmin.network.resources.ErrorResponse
import com.wms.superadmin.network.resources.SuccessResponse
import org.json.JSONObject
import org.koin.android.ext.android.inject
import retrofit2.HttpException
import java.lang.Exception
import kotlin.collections.ArrayList

class SrItemFragment : BaseFragment<FragmentSritemBinding>(R.layout.fragment_sritem),StockreportActivity.OnActivityListener{
    private val viewModel: SritemVM by viewModels<SritemVM>()
    private lateinit var mActivity:StockreportActivity
    private lateinit var mPreviousFragment: Fragment
    private var mStockReportRequest=StockReportRequest()
    private lateinit var mStockReportAdapter:StockReportAdapter
    override fun onInitialized(): Unit {
        viewModel.navArguments = arguments
        mStockReportAdapter = StockReportAdapter(viewModel.stockReportList.value?:mutableListOf(),ScreenNames.SR_ITEMWISE)
        binding.srRecyclerview.adapter = mStockReportAdapter
        mStockReportAdapter.setOnItemClickListener(
            object : StockReportAdapter.OnItemClickListener {
                override fun onItemClick(view:View, position:Int, item : StockReportRowModel) {
                    onClickRecyclerListinward(view, position, item)
                }
            })
        viewModel.stockReportList.observe(requireActivity()) {
            mStockReportAdapter.updateData(it as ArrayList<StockReportRowModel>)
        }

        binding.sritemVM = viewModel
        mActivity.setScreenAction(false,false,false,true,false)
    }
    override fun setUpClicks(): Unit {

    }

    fun onClickRecyclerListinward(view: View, position: Int, item: StockReportRowModel): Unit {
        var bundle=Bundle()
        mStockReportRequest.itemCode=item.ItemCode.toString()
       mActivity.mItemCode=item.ItemCode.toString()
        mStockReportRequest.inwardrate=item.InwardQTYRate.toString()
        bundle.putSerializable(IntentParams.STOCKREPORT_REQUEST,mStockReportRequest)
        bundle.putString(IntentParams.ITEM_NAME,item.ItemName)
        val destFragment = SrMonthFragment.getInstance(bundle,mActivity,this)
        requireActivity().loadFragment(
            R.id.fragmentContainer,
            destFragment,
            bundle = null,
            tag = SrMonthFragment.TAG,
            addToBackStack = true,
            add = true,
            enter = null,
            exit = null,)
    }
    fun getStockReport(){
        mStockReportRequest.screenName=ScreenNames.SR_ITEMWISE
        viewModel.onClickOnCreate(mStockReportRequest)
    }
    override fun onDetach() {
        super.onDetach()
        mActivity.mSubCatId="0"
        mActivity.setScreenAction(false,false,true,true,false)
         mActivity.onAttachFragment(mPreviousFragment)
    }

    override fun addObservers() {
        var progressDialog : AlertDialog? = null
        viewModel.progressLiveData.observe(requireActivity()) {
            if(it) {
                progressDialog?.dismiss()
                progressDialog = null
                progressDialog = requireActivity().showProgressDialog()
            } else  {
                progressDialog?.dismiss()
            }
        }
        viewModel.stockReportListLiveData.observe(requireActivity()) {
            if(it is SuccessResponse) {
                val response = it.getContentIfNotHandled()
                if(it.data.StockReportitemList?.isNotEmpty()!!){
                    mStockReportAdapter.updateMonthwise(mStockReportRequest.ismonthcheckbox!!)
                    binding.txtBranch.text=if(mStockReportRequest.ismonthcheckbox!!) ScreenNames.SR_MONTHWISE else ScreenNames.SR_ITEMWISE
                    viewModel.bindStockReportList(it.data.StockReportitemList?: arrayListOf())
                }

            } else if(it is ErrorResponse)  {
                onError(it.data ?: Exception())
            }
        }
    }
    private fun onError(exception: Exception) {
        when(exception) {
            is NoInternetConnection -> {
                Snackbar.make(binding.root, exception.message?:"", Snackbar.LENGTH_LONG).show()
            }
            is HttpException -> {
                val errorBody = exception.response()?.errorBody()?.string()
                val errorObject = if (errorBody != null  && errorBody.isJSONObject()) JSONObject(errorBody)
                else JSONObject()
                Snackbar.make(binding.root, MyApp.getInstance().getString(R.string.lbl_failure),
                    Snackbar.LENGTH_LONG).show()
            }
        }
    }






    companion object {
        const val TAG: String = "SR_Category_FRAGMENT"
        fun getInstance(bundle: Bundle?,activity: StockreportActivity,prious:Fragment): SrItemFragment {
            val fragment = SrItemFragment()
            fragment.mActivity=activity
            fragment.mPreviousFragment=prious
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun <T> onActionChange(`object`: T) {
        if(`object` is Boolean){
            if(`object`)
                Log.e("ActionChange","ItemWise:")
            mActivity.onBackPressed()
        }

        else if( `object` is StockReportRequest) {
            mStockReportRequest = `object` as StockReportRequest
            getStockReport()
        }
    }

    override fun <T> onSearchTextChange(searchtext: String) {
        if(this :: mStockReportAdapter.isInitialized )
            mStockReportAdapter.filter.filter(searchtext)
    }
}
