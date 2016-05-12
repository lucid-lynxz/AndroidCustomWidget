# AndroidCustomWidget
一些自定义控件集合

## CircleIndex 圆圈序号
![CircleIndex](https://raw.githubusercontent.com/lucid-lynxz/markdownPhotos/2f4e397b91e9f22d4fad932185cb4d25751b25a8/AndroidCustomWidget/CircleIndexDemo.png)
```xml
<org.lynxz.customwidgetlibrary.CircleIndexView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:foreColor="#00f" //设置颜色
    app:index="1" //序号值,推荐0~99 A~Z
    app:textSize="40sp"/> //圆圈内部值的字体大小,控件尽量这是为wrap_content
```