package com.saikou.sozo_tv.components.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.MenuPresenter
import androidx.appcompat.view.menu.MenuView
import androidx.appcompat.view.menu.SubMenuBuilder


@SuppressLint("RestrictedApi")
class NavigationSlidePresenter : MenuPresenter {

    private lateinit var menu: MenuBuilder
    lateinit var menuView: NavigationSlideMenuView
    var updateSuspended = false

    override fun initForMenu(context: Context, menu: MenuBuilder) {
        this.menu = menu
        menuView.initialize(this.menu)
    }

    override fun getMenuView(root: ViewGroup?): MenuView = menuView

    override fun updateMenuView(cleared: Boolean) {
        if (updateSuspended) return

        when {
            cleared -> menuView.buildMenuView()
            else -> menuView.updateMenuView()
        }
    }

    override fun setCallback(cb: MenuPresenter.Callback?) {}

    override fun onSubMenuSelected(subMenu: SubMenuBuilder?): Boolean = false

    override fun onCloseMenu(menu: MenuBuilder?, allMenusAreClosing: Boolean) {}

    override fun flagActionItems(): Boolean = false

    override fun expandItemActionView(menu: MenuBuilder?, item: MenuItemImpl?): Boolean = false

    override fun collapseItemActionView(menu: MenuBuilder?, item: MenuItemImpl?): Boolean = false

    override fun getId(): Int = MENU_PRESENTER_ID

    // The checked item is restored from the nav destination, so there is nothing of our own to
    // persist - but MenuBuilder calls these blind, and throwing here took the whole window down.
    override fun onSaveInstanceState(): Parcelable = Bundle()

    override fun onRestoreInstanceState(state: Parcelable?) {}


    companion object {
        const val MENU_PRESENTER_ID = 1
    }
}