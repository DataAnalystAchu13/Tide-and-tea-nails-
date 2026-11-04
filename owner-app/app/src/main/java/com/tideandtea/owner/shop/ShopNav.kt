package com.tideandtea.owner.shop

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Routes for the Orders and Website tabs. Tab roots are "orders" and "website". */
fun NavGraphBuilder.shopGraph(nav: NavController, vm: ShopViewModel, snackbar: SnackbarHostState) {
    val back: () -> Unit = { nav.popBackStack() }
    fun arg(e: androidx.navigation.NavBackStackEntry, k: String = "id") = e.arguments?.getString(k).orEmpty()

    composable("orders") { OrdersScreen(vm, snackbar) { id -> nav.navigate("order/$id") } }
    composable("order/{id}") { e -> OrderDetailScreen(vm, arg(e), snackbar, back) }

    composable("website") { WebsiteScreen(vm, snackbar) { route -> nav.navigate(route) } }
    composable("web/page/{id}") { e -> PageEditorScreen(vm, arg(e), snackbar, back) }
    composable("web/products") { ProductsScreen(vm, snackbar, back) { id -> nav.navigate("web/product/$id") } }
    composable("web/product/{id}") { e -> ProductEditorScreen(vm, arg(e), snackbar, back) }
    composable("web/discounts") { DiscountsScreen(vm, snackbar, back) { code -> nav.navigate("web/coupon/$code") } }
    composable("web/coupon/{id}") { e -> CouponEditorScreen(vm, arg(e), snackbar, back) }
    composable("web/posts") { PostsScreen(vm, snackbar, back) { id -> nav.navigate("web/post/$id") } }
    composable("web/post/{id}") { e -> PostEditorScreen(vm, arg(e), snackbar, back) }
    composable("web/reviews") { ReviewsScreen(vm, snackbar, back) { id -> nav.navigate("web/review/$id") } }
    composable("web/review/{id}") { e -> ReviewEditorScreen(vm, arg(e), snackbar, back) }
}
