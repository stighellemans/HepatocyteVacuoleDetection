# Hepatocyte Vacuole Detection in MAFLD using QuPath

This repository contains a QuPath script for the detection of vacuoles in hepatocyte cells, associated with Metabolic Associated Fatty Liver Disease (MAFLD), formerly known as Non-Alcoholic Fatty Liver Disease (NAFLD). The script utilizes the StarDist package for cell segmentation and a trained pixel classifier for vacuole detection.

## Features

1. **Cell Segmentation**: Partition large microscopic images into single hepatocyte cells using the StarDist package.
2. **Vacuole Detection**: Detect vacuoles present in the cytoplasm of hepatocytes with the use of a trained pixel classifier.
3. **Computational Efficiency**: The script divides large microscopic images into small tiles before applying the pixel classifier to optimize computational efficiency.
4. **Computational Immuno Histochemistry Quantification**: Measures the DAB- and Hematoxylin intensity for detailed histological analysis.

## Sample Images

### Vacuoles Present
![Vacuoles Present](https://github.com/user-attachments/assets/ffbfd9bf-bdce-4ce3-ad77-8e4e7d0db206)

### No Vacuoles Present
![No Vacuoles Present](https://github.com/user-attachments/assets/d0568783-0fc1-4c83-82e2-5f6f20d1114b)

## Getting Started

1. **Install QuPath**: Ensure QuPath is installed on your system. You can download it from the [QuPath website](https://qupath.github.io).
2. **Install StarDist**: Follow the [installation guide](https://github.com/stardist/stardist) for the StarDist package.
3. **Clone Repository**: Clone this repository to your local machine.
   ```sh
   git clone https://github.com/your-username/HepatocyteVacuoleDetection
   cd HepatocyteVacuoleDetection
   ```
4. **Run Script**: Open QuPath, load your images, and run the provided script to perform cell segmentation and vacuole detection.

## Requirements

- QuPath
- StarDist package
- Trained pixel classifier (included in this repository)

## Contributing

Feel free to contribute to this project by opening issues or submitting pull requests. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License.

## Acknowledgements

We acknowledge the developers of QuPath and StarDist for their powerful tools that made this project possible.
