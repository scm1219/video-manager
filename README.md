# video-manager


用于在多个移动硬盘上快速查找视频文件

使用sqlite作为数据库，每个磁盘拥有自己的索引文件，这样插入磁盘后，可以根据相对路径使得索引生效

使用方法

1、将.disk.needindex文件复制到需要索引的分区根目录,或者创建空文件.disk.needindex
2、java -jar XXX

可选功能，检测smart信息
从 https://www.smartmontools.org 下载 smartmontools，将smartctl复制到windows的system32目录下
对于非移动硬盘，SMART检测会失败