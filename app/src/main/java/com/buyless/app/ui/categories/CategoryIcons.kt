package com.buyless.app.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AirportShuttle
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Attractions
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Blender
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.ChildFriendly
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.DryCleaning
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiFoodBeverage
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Festival
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GolfCourse
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.House
import androidx.compose.material.icons.rounded.Houseboat
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Kayaking
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Liquor
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Loyalty
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Moped
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Plumbing
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.PriceCheck
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Recycling
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.RequestPage
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Soap
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Stroller
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material.icons.rounded.Synagogue
import androidx.compose.material.icons.rounded.TempleBuddhist
import androidx.compose.material.icons.rounded.TempleHindu
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material.icons.rounded.Tour
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** One titled section of the icon picker. */
@Immutable
data class CategoryIconGroup(val title: String, val icons: List<Pair<String, ImageVector>>)

/**
 * Every icon a custom category can use, grouped the way people think about spending.
 *
 * Icons are saved in the database by key (the icon's name, like "LocalCafe"), never by position,
 * so reordering or adding icons here can never change the icon of a category that already exists.
 * Each icon appears in one group only, so a key always maps to one place in the picker.
 */
val CategoryIconGroups: List<CategoryIconGroup> = listOf(
    CategoryIconGroup(
        "Food & drink",
        listOf(
            "Restaurant" to Icons.Rounded.Restaurant,
            "RestaurantMenu" to Icons.Rounded.RestaurantMenu,
            "Fastfood" to Icons.Rounded.Fastfood,
            "LocalPizza" to Icons.Rounded.LocalPizza,
            "LunchDining" to Icons.Rounded.LunchDining,
            "DinnerDining" to Icons.Rounded.DinnerDining,
            "BreakfastDining" to Icons.Rounded.BreakfastDining,
            "RamenDining" to Icons.Rounded.RamenDining,
            "RiceBowl" to Icons.Rounded.RiceBowl,
            "SetMeal" to Icons.Rounded.SetMeal,
            "KebabDining" to Icons.Rounded.KebabDining,
            "BakeryDining" to Icons.Rounded.BakeryDining,
            "Icecream" to Icons.Rounded.Icecream,
            "Cake" to Icons.Rounded.Cake,
            "Cookie" to Icons.Rounded.Cookie,
            "LocalCafe" to Icons.Rounded.LocalCafe,
            "Coffee" to Icons.Rounded.Coffee,
            "EmojiFoodBeverage" to Icons.Rounded.EmojiFoodBeverage,
            "LocalBar" to Icons.Rounded.LocalBar,
            "SportsBar" to Icons.Rounded.SportsBar,
            "WineBar" to Icons.Rounded.WineBar,
            "Liquor" to Icons.Rounded.Liquor,
            "LocalDrink" to Icons.Rounded.LocalDrink,
            "Egg" to Icons.Rounded.Egg,
            "Kitchen" to Icons.Rounded.Kitchen,
            "Blender" to Icons.Rounded.Blender,
        ),
    ),
    CategoryIconGroup(
        "Groceries & shopping",
        listOf(
            "ShoppingCart" to Icons.Rounded.ShoppingCart,
            "ShoppingBag" to Icons.Rounded.ShoppingBag,
            "ShoppingBasket" to Icons.Rounded.ShoppingBasket,
            "LocalGroceryStore" to Icons.Rounded.LocalGroceryStore,
            "Storefront" to Icons.Rounded.Storefront,
            "Store" to Icons.Rounded.Store,
            "LocalMall" to Icons.Rounded.LocalMall,
            "Checkroom" to Icons.Rounded.Checkroom,
            "DryCleaning" to Icons.Rounded.DryCleaning,
            "LocalOffer" to Icons.Rounded.LocalOffer,
            "Sell" to Icons.Rounded.Sell,
            "Redeem" to Icons.Rounded.Redeem,
            "CardGiftcard" to Icons.Rounded.CardGiftcard,
            "Diamond" to Icons.Rounded.Diamond,
            "Watch" to Icons.Rounded.Watch,
            "Chair" to Icons.Rounded.Chair,
            "Weekend" to Icons.Rounded.Weekend,
            "Bed" to Icons.Rounded.Bed,
        ),
    ),
    CategoryIconGroup(
        "Transport",
        listOf(
            "DirectionsCar" to Icons.Rounded.DirectionsCar,
            "LocalGasStation" to Icons.Rounded.LocalGasStation,
            "EvStation" to Icons.Rounded.EvStation,
            "LocalTaxi" to Icons.Rounded.LocalTaxi,
            "DirectionsBus" to Icons.Rounded.DirectionsBus,
            "Train" to Icons.Rounded.Train,
            "Tram" to Icons.Rounded.Tram,
            "Subway" to Icons.Rounded.Subway,
            "DirectionsBike" to Icons.Rounded.DirectionsBike,
            "TwoWheeler" to Icons.Rounded.TwoWheeler,
            "ElectricScooter" to Icons.Rounded.ElectricScooter,
            "Moped" to Icons.Rounded.Moped,
            "LocalParking" to Icons.Rounded.LocalParking,
            "Toll" to Icons.Rounded.Toll,
            "CarRepair" to Icons.Rounded.CarRepair,
            "Flight" to Icons.Rounded.Flight,
            "AirportShuttle" to Icons.Rounded.AirportShuttle,
            "DirectionsBoat" to Icons.Rounded.DirectionsBoat,
            "LocalShipping" to Icons.Rounded.LocalShipping,
        ),
    ),
    CategoryIconGroup(
        "Home & bills",
        listOf(
            "Home" to Icons.Rounded.Home,
            "House" to Icons.Rounded.House,
            "Apartment" to Icons.Rounded.Apartment,
            "Bolt" to Icons.Rounded.Bolt,
            "WaterDrop" to Icons.Rounded.WaterDrop,
            "Wifi" to Icons.Rounded.Wifi,
            "Router" to Icons.Rounded.Router,
            "PhoneAndroid" to Icons.Rounded.PhoneAndroid,
            "Tv" to Icons.Rounded.Tv,
            "LocalLaundryService" to Icons.Rounded.LocalLaundryService,
            "Plumbing" to Icons.Rounded.Plumbing,
            "Build" to Icons.Rounded.Build,
            "Handyman" to Icons.Rounded.Handyman,
            "CleaningServices" to Icons.Rounded.CleaningServices,
            "Lightbulb" to Icons.Rounded.Lightbulb,
            "Receipt" to Icons.Rounded.Receipt,
            "ReceiptLong" to Icons.Rounded.ReceiptLong,
            "RequestQuote" to Icons.Rounded.RequestQuote,
            "Description" to Icons.Rounded.Description,
            "Gavel" to Icons.Rounded.Gavel,
        ),
    ),
    CategoryIconGroup(
        "Health & care",
        listOf(
            "LocalHospital" to Icons.Rounded.LocalHospital,
            "MedicalServices" to Icons.Rounded.MedicalServices,
            "Medication" to Icons.Rounded.Medication,
            "Vaccines" to Icons.Rounded.Vaccines,
            "HealthAndSafety" to Icons.Rounded.HealthAndSafety,
            "Healing" to Icons.Rounded.Healing,
            "MonitorHeart" to Icons.Rounded.MonitorHeart,
            "Spa" to Icons.Rounded.Spa,
            "FaceRetouchingNatural" to Icons.Rounded.FaceRetouchingNatural,
            "ContentCut" to Icons.Rounded.ContentCut,
            "Soap" to Icons.Rounded.Soap,
            "Masks" to Icons.Rounded.Masks,
            "Psychology" to Icons.Rounded.Psychology,
            "SelfImprovement" to Icons.Rounded.SelfImprovement,
        ),
    ),
    CategoryIconGroup(
        "Sport & fitness",
        listOf(
            "FitnessCenter" to Icons.Rounded.FitnessCenter,
            "SportsSoccer" to Icons.Rounded.SportsSoccer,
            "SportsBasketball" to Icons.Rounded.SportsBasketball,
            "SportsTennis" to Icons.Rounded.SportsTennis,
            "SportsEsports" to Icons.Rounded.SportsEsports,
            "Pool" to Icons.Rounded.Pool,
            "Hiking" to Icons.Rounded.Hiking,
            "DirectionsRun" to Icons.Rounded.DirectionsRun,
            "DirectionsWalk" to Icons.Rounded.DirectionsWalk,
            "Kayaking" to Icons.Rounded.Kayaking,
            "Surfing" to Icons.Rounded.Surfing,
            "GolfCourse" to Icons.Rounded.GolfCourse,
            "SportsMartialArts" to Icons.Rounded.SportsMartialArts,
        ),
    ),
    CategoryIconGroup(
        "Fun & hobbies",
        listOf(
            "Movie" to Icons.Rounded.Movie,
            "TheaterComedy" to Icons.Rounded.TheaterComedy,
            "LocalActivity" to Icons.Rounded.LocalActivity,
            "MusicNote" to Icons.Rounded.MusicNote,
            "Headphones" to Icons.Rounded.Headphones,
            "Mic" to Icons.Rounded.Mic,
            "Piano" to Icons.Rounded.Piano,
            "Palette" to Icons.Rounded.Palette,
            "Brush" to Icons.Rounded.Brush,
            "PhotoCamera" to Icons.Rounded.PhotoCamera,
            "Videocam" to Icons.Rounded.Videocam,
            "Casino" to Icons.Rounded.Casino,
            "Toys" to Icons.Rounded.Toys,
            "Extension" to Icons.Rounded.Extension,
            "AutoStories" to Icons.Rounded.AutoStories,
            "MenuBook" to Icons.Rounded.MenuBook,
            "Park" to Icons.Rounded.Park,
            "Attractions" to Icons.Rounded.Attractions,
            "Celebration" to Icons.Rounded.Celebration,
            "Festival" to Icons.Rounded.Festival,
        ),
    ),
    CategoryIconGroup(
        "Travel",
        listOf(
            "Luggage" to Icons.Rounded.Luggage,
            "Hotel" to Icons.Rounded.Hotel,
            "BeachAccess" to Icons.Rounded.BeachAccess,
            "Map" to Icons.Rounded.Map,
            "Public" to Icons.Rounded.Public,
            "Explore" to Icons.Rounded.Explore,
            "Landscape" to Icons.Rounded.Landscape,
            "Cabin" to Icons.Rounded.Cabin,
            "Tour" to Icons.Rounded.Tour,
            "Houseboat" to Icons.Rounded.Houseboat,
            "Forest" to Icons.Rounded.Forest,
            "Anchor" to Icons.Rounded.Anchor,
        ),
    ),
    CategoryIconGroup(
        "Family & people",
        listOf(
            "ChildCare" to Icons.Rounded.ChildCare,
            "ChildFriendly" to Icons.Rounded.ChildFriendly,
            "Stroller" to Icons.Rounded.Stroller,
            "Face" to Icons.Rounded.Face,
            "Favorite" to Icons.Rounded.Favorite,
            "FamilyRestroom" to Icons.Rounded.FamilyRestroom,
            "Elderly" to Icons.Rounded.Elderly,
            "Group" to Icons.Rounded.Group,
            "People" to Icons.Rounded.People,
            "VolunteerActivism" to Icons.Rounded.VolunteerActivism,
            "Diversity3" to Icons.Rounded.Diversity3,
            "Handshake" to Icons.Rounded.Handshake,
        ),
    ),
    CategoryIconGroup(
        "Pets",
        listOf(
            "Pets" to Icons.Rounded.Pets,
            "Grass" to Icons.Rounded.Grass,
            "Yard" to Icons.Rounded.Yard,
        ),
    ),
    CategoryIconGroup(
        "Education & work",
        listOf(
            "School" to Icons.Rounded.School,
            "Science" to Icons.Rounded.Science,
            "Calculate" to Icons.Rounded.Calculate,
            "Work" to Icons.Rounded.Work,
            "BusinessCenter" to Icons.Rounded.BusinessCenter,
            "Laptop" to Icons.Rounded.Laptop,
            "Computer" to Icons.Rounded.Computer,
            "Print" to Icons.Rounded.Print,
            "Edit" to Icons.Rounded.Edit,
            "Terminal" to Icons.Rounded.Terminal,
            "Code" to Icons.Rounded.Code,
            "Badge" to Icons.Rounded.Badge,
        ),
    ),
    CategoryIconGroup(
        "Money",
        listOf(
            "Savings" to Icons.Rounded.Savings,
            "AccountBalance" to Icons.Rounded.AccountBalance,
            "AccountBalanceWallet" to Icons.Rounded.AccountBalanceWallet,
            "Payments" to Icons.Rounded.Payments,
            "CreditCard" to Icons.Rounded.CreditCard,
            "AttachMoney" to Icons.Rounded.AttachMoney,
            "CurrencyExchange" to Icons.Rounded.CurrencyExchange,
            "TrendingUp" to Icons.Rounded.TrendingUp,
            "ShowChart" to Icons.Rounded.ShowChart,
            "Paid" to Icons.Rounded.Paid,
            "PriceCheck" to Icons.Rounded.PriceCheck,
            "Loyalty" to Icons.Rounded.Loyalty,
            "Wallet" to Icons.Rounded.Wallet,
            "RequestPage" to Icons.Rounded.RequestPage,
            "Percent" to Icons.Rounded.Percent,
            "MoneyOff" to Icons.Rounded.MoneyOff,
        ),
    ),
    CategoryIconGroup(
        "Tech & subscriptions",
        listOf(
            "Smartphone" to Icons.Rounded.Smartphone,
            "PhoneIphone" to Icons.Rounded.PhoneIphone,
            "Devices" to Icons.Rounded.Devices,
            "Headset" to Icons.Rounded.Headset,
            "Memory" to Icons.Rounded.Memory,
            "Cloud" to Icons.Rounded.Cloud,
            "Subscriptions" to Icons.Rounded.Subscriptions,
            "Podcasts" to Icons.Rounded.Podcasts,
            "LiveTv" to Icons.Rounded.LiveTv,
            "SmartDisplay" to Icons.Rounded.SmartDisplay,
            "Gamepad" to Icons.Rounded.Gamepad,
            "Keyboard" to Icons.Rounded.Keyboard,
            "Mouse" to Icons.Rounded.Mouse,
            "Cable" to Icons.Rounded.Cable,
            "BatteryChargingFull" to Icons.Rounded.BatteryChargingFull,
            "SimCard" to Icons.Rounded.SimCard,
        ),
    ),
    CategoryIconGroup(
        "Faith & giving",
        listOf(
            "Mosque" to Icons.Rounded.Mosque,
            "Church" to Icons.Rounded.Church,
            "TempleHindu" to Icons.Rounded.TempleHindu,
            "TempleBuddhist" to Icons.Rounded.TempleBuddhist,
            "Synagogue" to Icons.Rounded.Synagogue,
        ),
    ),
    CategoryIconGroup(
        "Other",
        listOf(
            "Category" to Icons.Rounded.Category,
            "Star" to Icons.Rounded.Star,
            "Bookmark" to Icons.Rounded.Bookmark,
            "Label" to Icons.Rounded.Label,
            "Flag" to Icons.Rounded.Flag,
            "PushPin" to Icons.Rounded.PushPin,
            "Help" to Icons.Rounded.Help,
            "Lock" to Icons.Rounded.Lock,
            "Key" to Icons.Rounded.Key,
            "Schedule" to Icons.Rounded.Schedule,
            "Event" to Icons.Rounded.Event,
            "LocalFlorist" to Icons.Rounded.LocalFlorist,
            "EmojiEmotions" to Icons.Rounded.EmojiEmotions,
            "AutoAwesome" to Icons.Rounded.AutoAwesome,
            "Recycling" to Icons.Rounded.Recycling,
        ),
    ),
)

/** Key to icon, built once. Unknown keys (an icon removed in a later version) fall back to Category. */
private val iconsByKey: Map<String, ImageVector> by lazy {
    CategoryIconGroups.flatMap { it.icons }.toMap()
}

fun categoryIcon(key: String): ImageVector = iconsByKey[key] ?: Icons.Rounded.Category

/**
 * Search words per icon: the words in its name ("LocalCafe" -> "local cafe") plus its group title,
 * so typing "coffee", "cafe" or "food" all find something.
 */
internal val iconSearchText: Map<String, String> by lazy {
    CategoryIconGroups.flatMap { group ->
        group.icons.map { (key, _) ->
            key to (key.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2") + " " + group.title + " " + (IconSynonyms[key] ?: "")).lowercase()
        }
    }.toMap()
}

/** Extra words for icons whose names do not say what people would type. */
private val IconSynonyms = mapOf(
    "LocalCafe" to "coffee kopi",
    "Coffee" to "kopi",
    "EmojiFoodBeverage" to "tea teh",
    "RamenDining" to "noodles mee",
    "RiceBowl" to "nasi rice",
    "Fastfood" to "burger mcd",
    "LocalGasStation" to "petrol fuel minyak",
    "EvStation" to "charging electric",
    "Toll" to "tng touch n go",
    "TwoWheeler" to "motorcycle motor",
    "Bolt" to "electricity tnb letrik",
    "WaterDrop" to "water air",
    "Wifi" to "internet unifi",
    "Receipt" to "bill",
    "ReceiptLong" to "bill",
    "Savings" to "saving tabung",
    "AccountBalance" to "bank",
    "Payments" to "cash duit",
    "Subscriptions" to "netflix spotify",
    "Mosque" to "masjid zakat sedekah",
    "VolunteerActivism" to "charity donation derma",
    "Checkroom" to "clothes baju",
    "ContentCut" to "haircut barber",
    "Pets" to "cat dog kucing",
    "LocalHospital" to "clinic klinik doctor",
    "Medication" to "medicine ubat pharmacy",
    "School" to "tuition yuran fees",
    "Favorite" to "love date",
    "Celebration" to "party raya",
    "CardGiftcard" to "gift hadiah",
)
