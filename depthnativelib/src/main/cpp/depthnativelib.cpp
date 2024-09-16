#include <jni.h>
#include <string>

#include <libusb.h>
#include "depthai/depthai.hpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_depthnativelib_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_depthnativelib_depthAiCamera_prepareCameraJNI(JNIEnv *env, jobject thiz) {
    dai::Pipeline pipeline;
    auto colorCam = pipeline.create<dai::node::ColorCamera>();
    auto xlinkOut = pipeline.create<dai::node::XLinkOut>();
    xlinkOut->setStreamName("preview");
    colorCam->setInterleaved(true);
    colorCam->preview.link(xlinkOut->input);


//    try {
//        // Try connecting to device and start the pipeline
//        dai::Device device(pipeline);
//
//        // Get output queue
//        auto preview = device.getOutputQueue("preview");
//
//        cv::Mat frame;
//        while (true) {
//
//            // Receive 'preview' frame from device
//            auto imgFrame = preview->get<dai::ImgFrame>();
//
//            // Show the received 'preview' frame
//            cv::imshow("preview", cv::Mat(imgFrame->getHeight(), imgFrame->getWidth(), CV_8UC3, imgFrame->getData().data()));
//
//            // Wait and check if 'q' pressed
//            if (cv::waitKey(1) == 'q') return 0;
//
//        }
//    } catch (const std::runtime_error& err) {
//        std::cout << err.what() << std::endl;
//    }
//

}

class CameraInternal {
    public:
        JNIEnv jenv;
        void loadPipeline() {
         auto r = libusb_set_option(nullptr, LIBUSB_OPTION_ANDROID_JNIENV, this->jenv);
     }
};