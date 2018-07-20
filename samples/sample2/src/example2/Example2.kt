package example2

import com.soywiz.korim.vector.chart.*
import com.soywiz.korui.*
import com.soywiz.korui.event.*
import com.soywiz.korui.geom.len.*
import com.soywiz.korui.react.*
import com.soywiz.korui.style.*
import com.soywiz.korui.ui.*

fun main(args: Array<String>) = Application {
    //Logger.defaultLevel = Logger.Level.TRACE
    //Logger("korui-application").level = Logger.Level.TRACE
    reactFrame(ReactApp(), MyState(count = 0), "HELLO")
}


data class MyState(val count: Int)

class ReactApp : ReactComponent<MyState>() {
    override suspend fun Container.render() {
        vertical {
            inline {
                label("Count: ${state.count}")
                button("Click") {
                    mouse {
                        click {
                            state = state.copy(count = state.count + 1)
                        }
                    }
                }
            }
            inline {
                vectorImage(
                    ChartBars(
                        "hello" to 10,
                        "world" to 20 + state.count * 3,
                        "this" to 5,
                        "is" to 3 + state.count * 5,
                        "a" to 80,
                        "test" to 30
                    )
                ) {
                    width = 512.pt
                    height = 256.pt
                }
            }
        }
    }
}
