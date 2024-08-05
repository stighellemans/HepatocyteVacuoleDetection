// Import plugins 
//import qupath.tensorflow.stardist.StarDist2D
import qupath.ext.stardist.StarDist2D //using 0.3.0's version of StarDist. Comment this line and uncomment out the one above if on 0.2.3
import groovy.time.*
import static qupath.lib.gui.scripting.QPEx.* //default import for scripting
import qupath.lib.roi.*
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import qupath.lib.roi.*;


//!! deconvolution stains have to be set manually !!

//select all annotations drawn manually
selectAnnotations();

//make tiles
runPlugin('qupath.lib.algorithms.TilerPlugin', '{"tileSizeMicrons":200.0,"trimToROI":true,"makeAnnotations":true,"removeParentAnnotation":true}')
selectAnnotations();


/**
STARDIST
If you use this in published work, please remember to cite these:
 *  - the original StarDist paper (https://doi.org/10.48550/arXiv.1806.03535)
 *  - the original QuPath paper (https://doi.org/10.1038/s41598-017-17204-5)
 * Zaidi M., McKee T., Wouters B. (2021). Universal-StarDist-for-QuPath (https://github.com/MarkZaidi/Universal-StarDist-for-QuPath/) (version 1.0.0). Date released: 2021-08-03
**/
def model_trained_on_single_channel=0 //Set to 1 if the pretrained model you're using was trained on IF sections, set to 0 if trained on brightfield
param_channel=1 //channel to use for nucleus detection. First channel in image is channel 1. If working with H&E or HDAB, channel 1 is hematoxylin.
param_median=0 //median preprocessing: Requires an int value corresponding to the radius of the median filter kernel. For radii larger than 2, the image must be in uint8 bit depth. Default 0
param_divide=1 //division preprocessing: int or floating point, divides selected channel intensity by value before segmenting. Useful when normalization is disabled. Default 1
param_add=0 //addition preprocessing: int or floating point, add value to selected channel intensity before segmenting. Useful when normalization is disabled. Default 0
param_threshold = 0.5//threshold for detection. All cells segmented by StarDist will have a detection probability associated with it, where higher values indicate more certain detections. Floating point, range is 0 to 1. Default 0.5
param_pixelsize=0 //Pixel scale to perform segmentation at. Set to 0 for image resolution (default). Int values accepted, greater values  will be faster but may yield poorer segmentations.
param_tilesize=1024 //size of tile in pixels for processing. Must be a multiple of 16. Lower values may solve any memory-related errors, but can take longer to process. Default is 1024.
param_expansion=10 //size of cell expansion in pixels. Default is 10.
def min_nuc_area=0 //remove any nuclei with an area less than or equal to this value
nuc_area_measurement='Nucleus: Area µm^2'

def min_nuc_intensity=0.0 //remove any detections with an intensity less than or equal to this value
nuc_intensity_measurement='Hematoxylin: Nucleus: Mean'
normalize_low_pct=1 //lower limit for normalization. Set to 0 to disable
normalize_high_pct=99 // upper limit for normalization. Set to 100 to disable.

// Specify the model directory (you will need to change this!). Uncomment the model you wish to use
//Brightfield models
    def pathModel = '/Users/stighellemans/QuPath/v0.4/he_heavy_augment.pb'
    
//End of variables to set ******************************************************
param_channel=param_channel-1 // corrects for off-by-one error
normalize_high_pct=normalize_high_pct-0.000000000001 //corrects for some bizarre normalization issue when attempting to set 100 as the upper limit




// Specify whether the above model was trained using a single-channel image (e.g. IF DAPI)
// Get current image - assumed to have color deconvolution stains set
def imageData = getCurrentImageData()
def isBrightfield=imageData.isBrightfield()
def stains = imageData.getColorDeconvolutionStains() //will be null if IF

if (model_trained_on_single_channel == 0 && isBrightfield==true) {
    // If brightfield model and brightfield image
    println 'Performing detection on brightfield image using brightfield trained model'

    stardist = StarDist2D.builder(pathModel)
            .preprocess(
                    ImageOps.Filters.median(param_median),
                    ImageOps.Core.divide(param_divide),
                    ImageOps.Core.add(param_add)
            ) // Optional preprocessing (can chain multiple ops)

            .threshold(param_threshold)              // Prediction threshold
            .normalizePercentiles(normalize_low_pct,normalize_high_pct) // Percentile normalization. REQUIRED FOR IMC DATA
            .pixelSize(param_pixelsize)              // Resolution for detection
            //.doLog()
            .includeProbability(true)
            .measureIntensity()
            .tileSize(param_tilesize)
            .measureShape()
            .cellExpansion(param_expansion)
            .constrainToParent(false)
            .build()

} else {
    throw new Exception("Cannot use brightfield trained model to segment nuclei on IF image")
}
//Run stardist in selected annotation
def pathObjects = getSelectedObjects()
print(pathObjects)
if (pathObjects.isEmpty()) {
    Dialogs.showErrorMessage("StarDist", "Please select a parent object!")
    return
}
clearDetections()
def timeStart_CellDetection = new Date()
stardist.detectObjects(imageData, pathObjects)
TimeDuration CellDetection_duration = TimeCategory.minus(new Date(), timeStart_CellDetection)

//filter out small and low intensity nuclei


def toDelete = getDetectionObjects().findAll {measurement(it, nuc_area_measurement) <= min_nuc_area}
removeObjects(toDelete, true)
def toDelete2 = getDetectionObjects().findAll {measurement(it, nuc_intensity_measurement) <= min_nuc_intensity}
removeObjects(toDelete2, true)

println ('Stardist done in ' + CellDetection_duration)


/*
 * 
 *VACUOLE DETECTION
 * Run pixel classifier for each annotation
 */
 
selectAnnotations();
// make classifier maps
// REPLACE 'VacuoleDetectionClassifier' WITH YOUR OWN VACUOLE CLASSIFIER
createDetectionsFromPixelClassifier("VacuoleDetectionClassifier", 0.0, 0.0, "SELECT_NEW")

//fix for name of detection classes
getAnnotationObjects().each{annotation ->
    annotation.getChildObjects().each{detection ->
       String input = detection;
       String patternString = "\\((.*?)\\)";

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(input);

            if (matcher.find()) {
                String result = matcher.group(1);
               detection.setName(result)
           }

       }
    }
    

/* 
 *SPLIT CELLOBJECTS
 *split cellobjects into nuclei, cytoplasms, vacuoles
*/

//make some classes
def cellClass = getPathClass('cell')
color = getColorRGB(255,0,0)
cellClass.setColor(color)
def cellBodyClass = getPathClass('cellBody')
def nucleusClass = getPathClass('nucleus')
def cytoplasmClass = getPathClass('cytoplasm')
def vacuoleClass = getPathClass('vacuole')

//time stamp
println("Start vacuole detection")
timeStart_vacuoleDetection = new Date()

//loop over all tiles
getAnnotationObjects().each{annotation ->
    //get pixelclassifier maps
    cytoplasm = annotation.getChildObjects().findAll{it.getName() == "cytoplasm"}[0]
    vacuole = annotation.getChildObjects().findAll{it.getName() == "vacuole"}[0]
    
    //loop over all cells in tile
    annotation.getChildObjects().findAll{it.isCell()}.eachWithIndex{cell, index ->

        try {
            //split nucleus and cellbody
            cell.setName("cell "+index.toString())
            cell.setPathClass(cellClass)
            nucleus = PathObjects.createDetectionObject(cell.getNucleusROI(), cell.getPathClass())
            cellBodyROI = RoiTools.subtract(cell.getROI(),nucleus.getROI())
            cellBody = PathObjects.createDetectionObject(cellBodyROI, cell.getPathClass())
            
            //set name and class
            cellBody.setName("cellBody "+index.toString())
            cellBody.setPathClass(cellBodyClass)
            nucleus.setName("nucleus "+index.toString())
            nucleus.setPathClass(nucleusClass)
            
            //make the cellBody object a child of the cell
            cell.addChildObject(cellBody)
            cell.addChildObject(nucleus) 
        } catch (Exception e) {
            println("cellbody/nucleus splitting failed")
        }
        
        //edgedetection (for computing efficiency)
        try {
        IsOnEdge = RoiTools.difference(cell.getROI(), annotation.getROI()).getArea() != 0
        CellsOnEdge = []
        if (IsOnEdge) {
            removeObject(cell, false)
        }
        } catch (Exception e) {
            println("edgedetection failed")
        }
   
       //make ROI the intersect of the cellbody and the pixel classifier
       try{
           cellCytoplasmROI = RoiTools.subtract(RoiTools.intersection(cell.getROI(),cytoplasm.getROI()),cell.getNucleusROI())
           
           //only when cytoplasm exists in this cell
           if (cellCytoplasmROI.getArea() !=0) {
           cellCytoplasm = PathObjects.createDetectionObject(cellCytoplasmROI, cytoplasmClass)
           cellCytoplasm.setName("cytoplasm "+index.toString())
           cell.addChildObject(cellCytoplasm)
           }
       
       
       } catch (Exception e) {
           println("no cytoplasm exists")
       }
       
       try{
           cellVacuoleROI = RoiTools.subtract(RoiTools.intersection(cell.getROI(),vacuole.getROI()),cell.getNucleusROI())
           
           //only when vacuole exists in this cell
           if (cellVacuoleROI.getArea() !=0) {
           cellVacuole = PathObjects.createDetectionObject(cellVacuoleROI, vacuoleClass)
           cellVacuole.setName("vacuole "+index.toString())
           cell.addChildObject(cellVacuole)
           }
       } catch (Exception e) {
           println("no vacuole exists")
       }
   
    }
   
}
//time stamp
TimeDuration vacuoleDetectionDuration = TimeCategory.minus(new Date(), timeStart_vacuoleDetection)
println("Vacuole detection done in: "+vacuoleDetectionDuration)


// calculate intensities and other shape measurements
println("Calculating measurements")
selectDetections()
addShapeMeasurements("AREA")
//calculate DAB, Hematoxylin, Optical density sum
runPlugin('qupath.lib.algorithms.IntensityFeaturesPlugin', '{"pixelSizeMicrons":2.0,"region":"ROI","tileSizeMicrons":25.0,"colorOD":false,"colorStain1":true,"colorStain2":true,"colorStain3":false,"colorRed":false,"colorGreen":false,"colorBlue":false,"colorHue":false,"colorSaturation":false,"colorBrightness":false,"doMean":true,"doStdDev":true,"doMinMax":true,"doMedian":true,"doHaralick":false,"haralickDistance":1,"haralickBins":32}')


//save measurements
// REPLACE WITH YOUR OWN PATH TO SAVE
saveDetectionMeasurements('PATH_TO_SAVE')

//time stamp
TimeDuration totalDuration = TimeCategory.minus(new Date(), timeStart_CellDetection)
println ('Script done in: '+totalDuration)







