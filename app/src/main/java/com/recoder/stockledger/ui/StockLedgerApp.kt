package com.recoder.stockledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recoder.stockledger.data.TradeType
import kotlinx.coroutines.launch

private object Routes {
    const val Holdings = "holdings"
    const val Transactions = "transactions"
    const val TradeEntry = "trade-entry"
    const val TradeTypeArg = "tradeType"

    fun tradeEntry(type: TradeType): String = "$TradeEntry/${type.name}"
}

@Composable
fun StockLedgerApp(
    modifier: Modifier = Modifier,
    ledgerViewModel: LedgerViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val uiState by ledgerViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = Routes.Holdings,
        modifier = modifier,
    ) {
        composable(Routes.Holdings) {
            HoldingsRoute(
                summary = uiState.summary,
                holdings = uiState.holdings,
                onBuyClick = {
                    ledgerViewModel.openTradeEntry(TradeType.BUY)
                    navController.navigate(Routes.tradeEntry(TradeType.BUY))
                },
                onSellClick = {
                    ledgerViewModel.openTradeEntry(TradeType.SELL)
                    navController.navigate(Routes.tradeEntry(TradeType.SELL))
                },
                onDeleteHolding = ledgerViewModel::deleteHolding,
                onRefresh = ledgerViewModel::refreshQuotesByPull,
                onDestinationSelected = { destination ->
                    when (destination) {
                        TopLevelDestination.HOLDINGS -> Unit
                        TopLevelDestination.TRANSACTIONS -> navController.navigate(Routes.Transactions) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(Routes.Transactions) {
            TransactionsRoute(
                sections = uiState.transactionSections,
                selectedTradeFilter = uiState.selectedTradeFilter,
                selectedMarketFilter = uiState.selectedMarketFilter,
                onTradeFilterSelected = ledgerViewModel::selectTradeFilter,
                onMarketFilterSelected = ledgerViewModel::selectMarketFilter,
                onAddTradeClick = {
                    ledgerViewModel.openTradeEntry(TradeType.BUY)
                    navController.navigate(Routes.tradeEntry(TradeType.BUY))
                },
                onDestinationSelected = { destination ->
                    when (destination) {
                        TopLevelDestination.HOLDINGS -> navController.navigate(Routes.Holdings) {
                            launchSingleTop = true
                        }
                        TopLevelDestination.TRANSACTIONS -> Unit
                    }
                },
            )
        }

        composable(
            route = "${Routes.TradeEntry}/{${Routes.TradeTypeArg}}",
            arguments = listOf(navArgument(Routes.TradeTypeArg) { type = NavType.StringType }),
        ) {
            TradeEntryRoute(
                state = uiState.draft,
                sellCandidates = uiState.sellCandidates,
                symbolLookup = uiState.symbolLookup,
                symbolSuggestions = uiState.symbolSuggestions,
                canSubmit = uiState.canSubmitTrade,
                validationMessage = uiState.tradeValidationMessage,
                onBackClick = { navController.popBackStack() },
                onTradeTypeSelected = ledgerViewModel::selectTradeType,
                onSellCandidateSelected = ledgerViewModel::selectSellCandidate,
                onSymbolSuggestionSelected = ledgerViewModel::selectSymbolSuggestion,
                onMarketSelected = ledgerViewModel::selectTradeMarket,
                onSymbolChange = ledgerViewModel::onSymbolInputChanged,
                onDateChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(tradeDate = value) } },
                onPriceChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(priceLabel = value) } },
                onQuantityChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(quantityLabel = value) } },
                onCommissionChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(commissionLabel = value) } },
                onTaxChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(taxLabel = value) } },
                onNoteChange = { value -> ledgerViewModel.updateDraft { draft -> draft.copy(note = value) } },
                onSubmit = {
                    coroutineScope.launch {
                        if (ledgerViewModel.submitTrade()) {
                            navController.navigate(Routes.Transactions) {
                                popUpTo(Routes.Holdings)
                                launchSingleTop = true
                            }
                        }
                    }
                },
            )
        }
    }
}
