package com.example.pagingissue

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.container, Page1())
                .commit()
        }
    }
}

class Page1VM(app: Application) : AndroidViewModel(app) {
    val order = MutableStateFlow(true)

    val dao = Db.get(app).dao

    val items = order.flatMapLatest { order ->
        Pager(PagingConfig(pageSize = 10)) {
            if (order) {
                dao.itemsAsc()
            } else {
                dao.itemsDesc()
            }
        }.flow
    }.shareIn(viewModelScope, SharingStarted.Lazily)
}

class Page1 : Fragment(R.layout.page1) {
    private val vm: Page1VM by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.list)
        val adapter = object : PagingDataAdapter<Item, RecyclerView.ViewHolder>(
            diffCallback = object : DiffUtil.ItemCallback<Item>() {
                override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                    oldItem == newItem

                override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                    oldItem == newItem
            }
        ) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(TextView(parent.context)) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = getItem(position)?.title
            }
        }
        list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.items.collectLatest { data ->
                adapter.submitData(data)
            }
        }

        view.findViewById<View>(R.id.button).setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.container, Page2())
                .addToBackStack(null)
                .commit()
        }
    }
}

class Page2 : Fragment(R.layout.page2)

@Entity
data class Item(
    @PrimaryKey
    val id: Long,
    val title: String
)

@Database(version = 1, entities = [Item::class])
abstract class Db : RoomDatabase() {
    abstract val dao: ItemDao

    companion object {
        fun get(context: Context): Db {
            return Room.databaseBuilder(context, Db::class.java, "db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        try {
                            db.beginTransaction()
                            for (i in 0 until 100) {
                                db.insert(
                                    "Item",
                                    SQLiteDatabase.CONFLICT_REPLACE,
                                    ContentValues().apply {
                                        put("title", "Item $i")
                                    })
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                }).build()
        }
    }
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM Item ORDER BY title ASC")
    fun itemsAsc(): PagingSource<Int, Item>

    @Query("SELECT * FROM Item ORDER BY title DESC")
    fun itemsDesc(): PagingSource<Int, Item>
}
