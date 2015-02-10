#!/bin/bash

if [ ! -d "my_fs_python/" ]; then
	mkdir "my_fs_python/"
fi

if [ ! -d "tmpfs/" ]; then
	mkdir "tmpfs/"
fi

if [ ! -d "control/" ]; then
	mkdir "control/"
fi

#if [ ! -d "pyfs/" ]; then
#	mkdir "pyfs/"
#fi

if [ ! -d "yanc_test/" ]; then
	mkdir "yanc_test/"
fi

set -m

cd yanc

./yanc -f ../yanc_test &

cd ..

echo 'Beginning test on yanc hosts/ directory'

java -jar mkdirTester.jar yanc_test/hosts yanc10threads10dirs10triesInRoot.csv 10 10 True 10
java -jar mkdirTester.jar yanc_test/hosts yanc10threads10dirs10triesDedicatedDir.csv 10 10 False 10
java -jar mkdirTester.jar yanc_test/hosts yanc100threads100dirs10triesInRoot.csv 100 100 True 10
java -jar mkdirTester.jar yanc_test/hosts yanc100threads100dirs10triesDedicatedDir.csv 100 100 False 10

sudo umount yanc_test/
rm -r yanc_test/

echo 'Beginning test on my python Fuse file system'

python my_fs.py my_fs_python -f &

java -jar mkdirTester.jar my_fs_python/ mypython10threads10dirs10triesInRoot.csv 10 10 True 10
java -jar mkdirTester.jar my_fs_python/ mypython10threads10dirs10triesDedicatedDir.csv 10 10 False 10
java -jar mkdirTester.jar my_fs_python/ mypython100threads100dirs10triesInRoot.csv 100 100 True 10
java -jar mkdirTester.jar my_fs_python/ mypython100threads100dirs10triesDedicatedDir.csv 100 100 False 10

sudo umount my_fs_python/
rm -r my_fs_python/

echo 'Beginning test on tmpfs file system'

sudo mount -t tmpfs -o size=80m tmpfs tmpfs/

java -jar mkdirTester.jar tmpfs/ tmpfs10threads10dirs10triesInRoot.csv 10 10 True 10
java -jar mkdirTester.jar tmpfs/ tmpfs10threads10dirs10triesDedicatedDir.csv 10 10 False 10
java -jar mkdirTester.jar tmpfs/ tmpfs100threads100dirs10triesInRoot.csv 100 100 True 10
java -jar mkdirTester.jar tmpfs/ tmpfs100threads100dirs10triesDedicatedDir.csv 100 100 False 10

sudo umount tmpfs/
rm -r tmpfs/

echo 'Beginning control test on hard disk'

java -jar mkdirTester.jar control control10threads10dirs10triesInRoot.csv 10 10 True 10
java -jar mkdirTester.jar control control10threads10dirs10triesDedicatedDir.csv 10 10 False 10
java -jar mkdirTester.jar control control100threads100dirs10triesInRoot.csv 100 100 True 10
java -jar mkdirTester.jar control control100threads100dirs10triesDedicatedDir.csv 100 100 False 10

rm -r control


#sudo umount pyfs

